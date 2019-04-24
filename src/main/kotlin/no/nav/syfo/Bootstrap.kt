package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.JwtConfig
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.api.registerSykmeldingApi
import no.nav.syfo.db.Database
import no.nav.syfo.db.insertSykmelding
import no.nav.syfo.db.isSykmeldingStored
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.vault.Vault
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

data class BehandlingsUtfallReceivedSykmelding(val receivedSykmelding: ByteArray, val behandlingsUtfall: ByteArray)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")

val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

@KtorExperimentalAPI
fun main() = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val environment = Environment()
    val vaultSecrets =
        objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(environment, vaultSecrets)
        .envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )
    val streamProperties =
        kafkaBaseConfig.toStreamsConfig(environment.applicationName, valueSerde = Serdes.String()::class)
    val kafkaStream = createKafkaStream(streamProperties, environment)

    kafkaStream.start()

    val database = Database(environment)

    launch(backgroundTasksContext) {
        try {
            Vault.renewVaultTokenTask(applicationState)
        } finally {
            applicationState.running = false
        }
    }

    launch(backgroundTasksContext) {
        try {
            database.runRenewCredentialsTask { applicationState.running }
        } finally {
            applicationState.running = false
        }
    }

    val applicationServer = embeddedServer(Netty, environment.applicationPort) {
        initRouting(applicationState, database, vaultSecrets, environment)
    }.start(wait = false)

    launchListeners(environment, applicationState, consumerProperties, database)

    Runtime.getRuntime().addShutdownHook(Thread {
        kafkaStream.close()
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val sm2013InputStream = streamsBuilder.stream<String, String>(
        listOf(
            env.sm2013ManualHandlingTopic,
            env.kafkaSm2013AutomaticDigitalHandlingTopic,
            env.smpapirManualHandlingTopic,
            env.kafkaSm2013AutomaticPapirmottakTopic,
            env.sm2013InvalidHandlingTopic
        ), Consumed.with(Serdes.String(), Serdes.String())
    )

    val behandlingsUtfallStream = streamsBuilder.stream<String, String>(
        listOf(
            env.sm2013BehandlingsUtfallTopic
        ), Consumed.with(Serdes.String(), Serdes.String())
    )

    val joinWindow = JoinWindows.of(TimeUnit.DAYS.toMillis(14))
        .until(TimeUnit.DAYS.toMillis(31))

    val joined = Joined.with(
        Serdes.String(), Serdes.String(), Serdes.String()
    )

    sm2013InputStream.join(behandlingsUtfallStream, { sm2013, behandling ->
        objectMapper.writeValueAsString(
            BehandlingsUtfallReceivedSykmelding(
                receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                behandlingsUtfall = behandling.toByteArray(Charsets.UTF_8)
            )
        )
    }, joinWindow, joined)
        .to(env.sm2013RegisterTopic, Produced.with(Serdes.String(), Serdes.String()))

    return KafkaStreams(streamsBuilder.build(), streamProperties)
}

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    database: Database
) {
    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.sm2013RegisterTopic))

                blockingApplicationLogic(applicationState, kafkaconsumer, database)
            }
        }.toList()

        applicationState.initialized = true
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.running) {
        var logValues = arrayOf(
            StructuredArguments.keyValue("smId", "missing"),
            StructuredArguments.keyValue("organizationNumber", "missing"),
            StructuredArguments.keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding =
                objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult =
                objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)

            logValues = arrayOf(
                StructuredArguments.keyValue("msgId", receivedSykmelding.msgId),
                StructuredArguments.keyValue("mottakId", receivedSykmelding.navLogId),
                StructuredArguments.keyValue("orgNr", receivedSykmelding.legekontorOrgNr),
                StructuredArguments.keyValue("smId", receivedSykmelding.sykmelding.id)
            )

            log.info("Received a SM2013, going to persist it in DB, $logKeys", *logValues)

            if (database.isSykmeldingStored(receivedSykmelding.sykmelding.id)) {
                log.warn("Message with {} marked as already stored in the database, $logKeys", *logValues)
            } else {

                try {
                    database.insertSykmelding(
                        PersistedSykmelding(
                            id = receivedSykmelding.sykmelding.id,
                            pasientFnr = receivedSykmelding.personNrPasient,
                            pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                            legeFnr = receivedSykmelding.personNrLege,
                            legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
                            mottakId = receivedSykmelding.navLogId,
                            legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                            legekontorHerId = receivedSykmelding.legekontorHerId,
                            legekontorReshId = receivedSykmelding.legekontorReshId,
                            epjSystemNavn = receivedSykmelding.sykmelding.avsenderSystem.navn,
                            epjSystemVersjon = receivedSykmelding.sykmelding.avsenderSystem.versjon,
                            mottattTidspunkt = receivedSykmelding.mottattDato,
                            sykmelding = receivedSykmelding.sykmelding,
                            behandlingsUtfall = validationResult

                        )
                    )
                    log.info("SM2013, stored in the database, $logKeys", *logValues)
                    MESSAGE_STORED_IN_DB_COUNTER.inc()
                } catch (e: Exception) {
                    log.error("Exception caught while handling message $logKeys", *logValues, e)
                }
            }
        }
        delay(100)
    }
}

@KtorExperimentalAPI
fun Application.initRouting(
    applicationState: ApplicationState,
    database: Database,
    vaultSecrets: VaultSecrets,
    environment: Environment
) {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
        }
    }
    install(Authentication) {
        jwt {
            log.info("Auth: Starting authentication...")
            val jwtConfig = JwtConfig(vaultSecrets, environment)
            realm = JwtConfig.REALM
            verifier(jwtConfig.jwkProvider, vaultSecrets.jwtIssuer)
            validate { credentials ->
                log.info("Auth: User requested resource '${request.url()}'")
                jwtConfig.validate(credentials)
            }
        }
    }
    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(HttpHeaders.XCorrelationId)
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")

            log.error("Caught exception", cause)
            throw cause
        }
    }
    routing {
        registerNaisApi(applicationState)
        authenticate {
            registerSykmeldingApi(database)
        }
    }
}

internal fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    return "${origin.scheme}://${origin.host}$port${origin.uri}"
}
