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
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments

import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.db.Database
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.receivedSykmelding.Sykmelding
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.jetbrains.exposed.sql.insert
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
private val log = LoggerFactory.getLogger("nav.syfo.syfosmregister")

@ObsoleteCoroutinesApi
fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..config.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(config, credentials, valueDeserializer = StringDeserializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.kafkaSm2013AutomaticPapirmottakTopic, config.kafkaSm2013AutomaticDigitalHandlingTopic))
                Database.init(config)
                blockingApplicationLogic(applicationState, kafkaconsumer)
            }
        }.toList()

        runBlocking {
            Runtime.getRuntime().addShutdownHook(Thread {
                applicationServer.stop(10, 10, TimeUnit.SECONDS)
            })

            applicationState.initialized = true
            listeners.forEach { it.join() }
        }
    } finally {
        applicationState.running = false
    }
}

@ObsoleteCoroutinesApi
suspend fun blockingApplicationLogic(applicationState: ApplicationState, kafkaconsumer: KafkaConsumer<String, String>) {
    while (applicationState.running) {
        var logValues = arrayOf(
                StructuredArguments.keyValue("smId", "missing"),
                StructuredArguments.keyValue("organizationNumber", "missing"),
                StructuredArguments.keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
            logValues = arrayOf(
                    StructuredArguments.keyValue("msgId", receivedSykmelding.msgId),
                    StructuredArguments.keyValue("smId", receivedSykmelding.navLogId),
                    StructuredArguments.keyValue("orgNr", receivedSykmelding.legekontorOrgNr)
            )

            log.info("Received a SM2013, going to persist it in DB, $logKeys", *logValues)

            // TODO Trying to get postgress SQL user, name and token, postgress DB

            Database.dbQuery {
                 Sykmelding.insert {
                    it[aktoerIdPasient] = receivedSykmelding.aktoerIdPasient
                    it[aktoerIdLege] = receivedSykmelding.aktoerIdLege
                    it[navLogId] = receivedSykmelding.navLogId
                    it[msgId] = receivedSykmelding.msgId
                    it[legekontorOrgNr] = receivedSykmelding.legekontorOrgNr
                    it[legekontorOrgName] = receivedSykmelding.legekontorOrgName
                    it[mottattDato] = DateTime(receivedSykmelding.mottattDato.year, receivedSykmelding.mottattDato.monthValue, receivedSykmelding.mottattDato.dayOfMonth, receivedSykmelding.mottattDato.hour, receivedSykmelding.mottattDato.minute)
                }
            }
                /*
            SykmeldingService().leggtilSykmelding(
                    NySykmelding.Sykmelding(
                            aktoerIdPasient = receivedSykmelding.aktoerIdPasient,
                            aktoerIdLege = receivedSykmelding.aktoerIdLege,
                            navLogId = receivedSykmelding.navLogId,
                            msgId = receivedSykmelding.msgId,
                            legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                            legekontorOrgName = receivedSykmelding.legekontorOrgName,
                            mottattDato = DateTime(receivedSykmelding.mottattDato.year, receivedSykmelding.mottattDato.monthValue, receivedSykmelding.mottattDato.dayOfMonth, receivedSykmelding.mottattDato.hour, receivedSykmelding.mottattDato.minute)
                    )
            )
            */
            log.info("SM2013, saved i table Sykmelding, $logKeys", *logValues)
        }
    }
    delay(100)
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