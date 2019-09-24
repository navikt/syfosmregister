package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.aksessering.api.registerSykmeldingApi
import no.nav.syfo.api.getWellKnown
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.erBehandlingsutfallLagret
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.persistering.opprettTomSykmeldingsmetadata
import no.nav.syfo.vault.Vault
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
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

    val vaultCredentialService = VaultCredentialService()
    val database = Database(environment, vaultCredentialService)

    launch(backgroundTasksContext) {
        try {
            Vault.renewVaultTokenTask(applicationState)
        } finally {
            applicationState.running = false
        }
    }

    launch(backgroundTasksContext) {
        try {
            vaultCredentialService.runRenewCredentialsTask { applicationState.running }
        } finally {
            applicationState.running = false
        }
    }

    val applicationServer = embeddedServer(Netty, environment.applicationPort) {
        initRouting(applicationState, database, vaultSecrets, environment.cluster)
    }.start(wait = false)

    launchListeners(
        environment,
        applicationState,
        database,
        consumerProperties
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.running = false
        }
    }

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    database: Database,
    consumerProperties: Properties
) {
    val recievedSykmeldingListeners = 0.until(env.applicationThreads).map {
        val kafkaconsumerRecievedSykmelding = KafkaConsumer<String, String>(consumerProperties)

        kafkaconsumerRecievedSykmelding.subscribe(
            listOf(
                env.sm2013ManualHandlingTopic,
                env.kafkaSm2013AutomaticDigitalHandlingTopic,
                env.smpapirManualHandlingTopic,
                env.kafkaSm2013AutomaticPapirmottakTopic,
                env.sm2013InvalidHandlingTopic
            )
        )

        createListener(applicationState) {
            blockingApplicationLogicRecievedSykmelding(applicationState, kafkaconsumerRecievedSykmelding, database)
        }
    }.toList()

    val behandlingsutfallListeners = 0.until(env.applicationThreads).map {
        val kafkaconsumerBehandlingsutfall = KafkaConsumer<String, String>(consumerProperties)

        kafkaconsumerBehandlingsutfall.subscribe(
            listOf(
                env.sm2013BehandlingsUtfallTopic
            )
        )

        createListener(applicationState) {
            blockingApplicationLogicBehandlingsutfall(applicationState, kafkaconsumerBehandlingsutfall, database)
        }
    }.toList()

    applicationState.initialized = true
    runBlocking { (recievedSykmeldingListeners + behandlingsutfallListeners).forEach { it.join() } }
}

suspend fun blockingApplicationLogicRecievedSykmelding(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.running) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
            )
            handleMessageSykmelding(receivedSykmelding, database, loggingMeta)
        }
        delay(100)
    }
}

suspend fun handleMessageSykmelding(
    receivedSykmelding: ReceivedSykmelding,
    database: Database,
    loggingMeta: LoggingMeta
) = coroutineScope {
    wrapExceptions(loggingMeta) {
        log.info("Mottatt sykmelding SM2013, {}", fields(loggingMeta))

        if (database.connection.erSykmeldingsopplysningerLagret(receivedSykmelding.sykmelding.id)) {
            log.error("Sykmelding med id {} allerede lagret i databasen, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
        } else {

                database.connection.opprettSykmeldingsopplysninger(
                        Sykmeldingsopplysninger(
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
                                tssid = receivedSykmelding.tssid
                        )
                )
                database.connection.opprettSykmeldingsdokument(
                        Sykmeldingsdokument(
                                id = receivedSykmelding.sykmelding.id,
                                sykmelding = receivedSykmelding.sykmelding
                        )
                )
                database.connection.opprettTomSykmeldingsmetadata(receivedSykmelding.sykmelding.id)

                log.info("Sykmelding SM2013 lagret i databasen, {}", fields(loggingMeta))
                MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}

suspend fun blockingApplicationLogicBehandlingsutfall(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.running) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val sykmeldingsid = it.key()
            val validationResult: ValidationResult = objectMapper.readValue(it.value())

            val loggingMeta = LoggingMeta(
                    mottakId = "",
                    orgNr = "",
                    msgId = "",
                    sykmeldingId = sykmeldingsid
            )

            handleMessageBehandlingsutfall(validationResult, sykmeldingsid, database, loggingMeta)
        }
        delay(100)
    }
}

suspend fun handleMessageBehandlingsutfall(
    validationResult: ValidationResult,
    sykmeldingsid: String,
    database: Database,
    loggingMeta: LoggingMeta
) = coroutineScope {
    wrapExceptions(loggingMeta) {
        log.info("Mottatt behandlingsutfall, {}", fields(loggingMeta))

        if (database.connection.erBehandlingsutfallLagret(sykmeldingsid)) {
            log.error(
                    "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen, {}", fields(loggingMeta)
            )
        } else {

                database.connection.opprettBehandlingsutfall(
                        Behandlingsutfall(
                                id = sykmeldingsid,
                                behandlingsutfall = validationResult
                        )
                )

                log.info("Behandlingsutfall lagret i databasen, {}", fields(loggingMeta))
        }
    }
}

@KtorExperimentalAPI
fun Application.initRouting(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    vaultSecrets: VaultSecrets,
    cluster: String
) {
    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, wellKnown.issuer)
            validate { credentials ->
                if (!credentials.payload.audience.contains(vaultSecrets.loginserviceClientId)) {
                    log.warn(
                        "Auth: Unexpected audience for jwt {}, {}, {}",
                        keyValue("issuer", credentials.payload.issuer),
                        keyValue("audience", credentials.payload.audience),
                        keyValue("expectedAudience", vaultSecrets.loginserviceClientId)
                    )
                    null
                } else {
                    JWTPrincipal(credentials.payload)
                }
            }
        }
        basic(name = "basic") {
            validate { credentials ->
                if (credentials.name == vaultSecrets.syfomockUsername && credentials.password == vaultSecrets.syfomockPassword) {
                    UserIdPrincipal(credentials.name)
                } else null
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

    val sykmeldingService = SykmeldingService(database)
    routing {
        registerNaisApi(applicationState)
        authenticate("jwt") {
            registerSykmeldingApi(sykmeldingService)
        }
        authenticate("basic") {
            registerNullstillApi(database, cluster)
        }
    }
}
