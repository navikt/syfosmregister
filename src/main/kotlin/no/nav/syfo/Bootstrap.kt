package no.nav.syfo

import com.ctc.wstx.exc.WstxException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
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
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.loadBaseConfig
import no.nav.syfo.util.toConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jms.MessageProducer
import javax.jms.Session
import kotlinx.coroutines.slf4j.MDCContext
import no.nav.syfo.vault.Vault
import org.slf4j.Logger
import java.io.IOException
import java.lang.IllegalStateException

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")

private val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

fun main() = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val secrets: VaultSecrets = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(config, secrets)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${config.applicationName}-consumer", valueDeserializer = StringDeserializer::class)

    val database = Database(config)

    launchBackgroundTask(
            applicationState = applicationState,
            callName = "Vault - Token Renewal Task"
    ) {
        Vault.renewVaultTokenTask(applicationState)
    }
    launchBackgroundTask(
            applicationState = applicationState,
            callName = "DB - Credentials Renewal Task"
    ) {
        database.runRenewCredentialsTask(database.init()) { applicationState.running }
    }

    launch {
        try {
            database.init()
        } catch (e: Exception) {
            log.error("Database error(s)", e)
            applicationState.running = false
        }
    }

    connectionFactory(config).createConnection(secrets.mqUsername, secrets.mqPassword).use { connection ->
        connection.start()

    try {
        val listeners = (1..config.applicationThreads).map {
            launch {
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.sm2013ManualHandlingTopic, config.kafkaSm2013AutomaticDigitalHandlingTopic))

                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
                val backoutQueue = session.createQueue(config.backoutQueueName)
                val backoutProducer = session.createProducer(backoutQueue)

                blockingApplicationLogic(applicationState, kafkaconsumer, database, backoutProducer, session)
            }
        }.toList()

        applicationState.initialized = true

        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database,
    backoutProducer: MessageProducer,
    session: Session
) {
    while (applicationState.running) {
        var logValues = arrayOf(
                StructuredArguments.keyValue("smId", "missing"),
                StructuredArguments.keyValue("organizationNumber", "missing"),
                StructuredArguments.keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
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
                        sykmelding = receivedSykmelding.sykmelding

                ))
                log.info("SM2013, stored in the database, $logKeys", *logValues)
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            } catch (e: Exception) {
                log.error("Exception caught while handling message, sending to backout $logKeys", *logValues, e)
                backoutProducer.send(session.createTextMessage().apply {
                    text = objectMapper.writeValueAsString(receivedSykmelding)
                })
            }
        }
        delay(100)
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}

private fun CoroutineScope.launchBackgroundTask(
    applicationState: ApplicationState,
    callName: String,
    block: suspend () -> Any
) {
    launch(backgroundTasksContext) {
        try {
            retryAsync(callName, IOException::class, WstxException::class, IllegalStateException::class) {
                block()
            }
        } catch (e: Throwable) {
            log.error("Background task '$callName' was cancelled, failing self tests", e)
            applicationState.running = false
        }
    }
}