package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArguments
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.api.registerNaisApi
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

private val log = LoggerFactory.getLogger("nav.syfo.syfosmregister")

fun main(args: Array<String>) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(env, valueDeserializer = StringDeserializer::class)
                val kafkaconsumer = KafkaConsumer<String, String>(consumerProperties)
                kafkaconsumer.subscribe(listOf(env.kafkaSm2013AutomaticPapirmottakTopic, env.kafkaSm2013AutomaticDigitalHandlingTopic))

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
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(it.value())) as XMLEIFellesformat
            logValues = arrayOf(
                    StructuredArguments.keyValue("smId", fellesformat.get<XMLMottakenhetBlokk>().ediLoggId),
                    StructuredArguments.keyValue("organizationNumber", extractOrganisationNumberFromSender(fellesformat)?.id),
                    StructuredArguments.keyValue("msgId", fellesformat.get<XMLMsgHead>().msgInfo.msgId)
            )
            log.info("Received a SM2013, going to persist it in DB, $logKeys", *logValues)
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

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "ENH"
        }
