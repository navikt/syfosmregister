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
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.vault.getVaultToken
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
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
    // Database.init(config)

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..config.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(config, credentials, valueDeserializer = StringDeserializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(config.kafkaSm2013AutomaticPapirmottakTopic, config.kafkaSm2013AutomaticDigitalHandlingTopic))
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

            val vaultToken = getVaultToken()
            log.info("Got token (token=$vaultToken)")

            /*
            val vault = VaultUtil().vaultClient

            val path = "postgresql/preprod-fss/creds/admin"
            log.info("Renewing database credentials for rolea dmin")
            val response = vault.logical().read(path)
            val username = response.data["username"]
            val password = response.data["password"]
            log.info("Got new credentials (username=$username)")
            */

            // TODO Trying to get postgress SQL user, name and token

            // TODO implement postgress DB
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