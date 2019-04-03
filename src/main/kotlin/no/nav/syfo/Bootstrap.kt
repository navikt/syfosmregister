package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
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
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.db.Database
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.db.insertSykmelding
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.PersistedSykmelding
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.vault.Vault
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.apache.kafka.streams.kstream.Joined
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.Logger
import java.nio.file.Paths
import java.util.Properties

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

data class BehandlingsUtfallReceivedSykmelding(val receivedSykmelding: ByteArray, val behandlingsUtfall: ByteArray)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")

val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

fun main() = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
            .envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = Serdes.String()::class)
    val kafkaStream = createKafkaStream(streamProperties, env)

    kafkaStream.start()

    val database = Database(env)

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

    launchListeners(env, applicationState, consumerProperties, database)

    Runtime.getRuntime().addShutdownHook(Thread {
        kafkaStream.close()
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val sm2013InputStream = streamsBuilder.stream<String, String>(listOf(
            env.sm2013ManualHandlingTopic,
            env.kafkaSm2013AutomaticDigitalHandlingTopic,
            env.smpapirManualHandlingTopic,
            env.kafkaSm2013AutomaticPapirmottakTopic,
            env.sm2013InvalidHandlingTopic), Consumed.with(Serdes.String(), Serdes.String()))

    val behandlingsUtfallStream = streamsBuilder.stream<String, String>(listOf(
            env.sm2013BehandlingsUtfallTopic), Consumed.with(Serdes.String(), Serdes.String()))

    val joinWindow = JoinWindows.of(TimeUnit.DAYS.toMillis(14))
            .until(TimeUnit.DAYS.toMillis(31))

    val joined = Joined.with(
            Serdes.String(), Serdes.String(), Serdes.String())

    sm2013InputStream.join(behandlingsUtfallStream, { sm2013, behandling ->
        objectMapper.writeValueAsString(
                BehandlingsUtfallReceivedSykmelding(
                        receivedSykmelding = sm2013.toByteArray(Charsets.UTF_8),
                        behandlingsUtfall = behandling.toByteArray(Charsets.UTF_8)
                ))
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
            val behandlingsUtfallReceivedSykmelding: BehandlingsUtfallReceivedSykmelding = objectMapper.readValue(it.value())
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(behandlingsUtfallReceivedSykmelding.receivedSykmelding)
            val validationResult: ValidationResult = objectMapper.readValue(behandlingsUtfallReceivedSykmelding.behandlingsUtfall)

            logValues = arrayOf(
                    StructuredArguments.keyValue("msgId", receivedSykmelding.msgId),
                    StructuredArguments.keyValue("smId", receivedSykmelding.navLogId),
                    StructuredArguments.keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
            )

            log.info("Received a SM2013, going to persist it in DB, $logKeys", *logValues)
            try {
                database.insertSykmelding(PersistedSykmelding(
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

                ))
                log.info("SM2013, stored in the database, $logKeys", *logValues)
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            } catch (e: Exception) {
                log.error("Exception caught while handling message $logKeys", *logValues, e)
            }
        }
        delay(100)
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = { applicationState.initialized },
                livenessCheck = { applicationState.running }
        )
    }
}
