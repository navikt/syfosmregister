package no.nav.syfo.sykmeldingstatus.kafka.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer

class SykmeldingStatusConsumerServiceTest : Spek({
    val kafka = KafkaContainer()
    kafka.start()
    fun setupKafkaConfig(): Properties {
        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        return kafkaConfig
    }

    val kafkaConfig = setupKafkaConfig()

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    fun getKafkaConsumer(): KafkaConsumer<String, SykmeldingStatusKafkaMessage> {
        return KafkaConsumer<String, SykmeldingStatusKafkaMessage>(kafkaConfig, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessage::class))
    }

    fun getKafkaProducer(): SykmeldingStatusKafkaProducer {
        return SykmeldingStatusKafkaProducer(KafkaProducer(kafkaConfig), "topic")
    }

    var applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusService, SykmeldingStatusKafkaConsumer(getKafkaConsumer(), listOf("topic")), applicationState, listOf("syfosmregister"))

    beforeGroup {
        kafka.start()
    }
    afterGroup {
        kafka.stop()
    }

    beforeEachTest { clearAllMocks(); applicationState.alive = true; }

    describe("SykmeldingStatusConsumerServiceTest publish and read from kafka") {
        it("Test send status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                var sykmeldingSendEvent: SykmeldingSendEvent? = null
                every { sykmeldingStatusService.registrerSendt(any()) } answers {
                    sykmeldingSendEvent = args.get(0) as SykmeldingSendEvent
                    applicationState.alive = false
                }

                val kafkaProducer = getKafkaProducer()
                kafkaProducer.send(getSykmeldingKafkaMessage(sykmeldingId, StatusEventDTO.SENDT), "syfoservice")
                sykmeldingStatusConsumerService.start()

                sykmeldingSendEvent?.sykmeldingId shouldEqual sykmeldingId
            }
        }

        it("Test bekreft status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                var sykmeldingBekreftEvent: SykmeldingBekreftEvent? = null
                every { sykmeldingStatusService.registrerBekreftet(any()) } answers {
                    sykmeldingBekreftEvent = args[0] as SykmeldingBekreftEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(getSykmeldingKafkaMessage(sykmeldingId, StatusEventDTO.BEKREFTET), "syfoservice")

                sykmeldingStatusConsumerService.start()

                sykmeldingBekreftEvent?.sykmeldingId shouldEqual sykmeldingId
            }
        }

        it("Test utgatt event") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(getSykmeldingKafkaMessage(sykmeldingId, StatusEventDTO.UTGATT), "syfoservice")

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent?.sykmeldingId shouldEqual sykmeldingId
                sykmeldingStatusEvent?.event shouldEqual StatusEvent.UTGATT
            }
        }
        it("Test apen event") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(getSykmeldingKafkaMessage(sykmeldingId, StatusEventDTO.APEN), "syfoservice")

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent?.sykmeldingId shouldEqual sykmeldingId
                sykmeldingStatusEvent?.event shouldEqual StatusEvent.APEN
            }
        }

        it("Test avbrutt event") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(getSykmeldingKafkaMessage(sykmeldingId, StatusEventDTO.AVBRUTT), "syfoservice")

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent?.sykmeldingId shouldEqual sykmeldingId
                sykmeldingStatusEvent?.event shouldEqual StatusEvent.AVBRUTT
            }
        }

        it("test many events") {
            runBlocking {
                val events = 100
                val statusEvents = 0.until(events).map {
                    getSykmeldingKafkaMessage(UUID.randomUUID().toString(), getRandomStatus())
                }
                var count = 0
                val func = {
                    count++
                    if (count >= 100) applicationState.alive = false
                }
                every { sykmeldingStatusService.registrerStatus(any()) } answers { func() }
                every { sykmeldingStatusService.registrerBekreftet(any()) } answers { func() }
                every { sykmeldingStatusService.registrerSendt(any()) } answers { func() }
                val kafkaProducer = getKafkaProducer()
                statusEvents.forEach { sykmeldingStatusKafkaMessage ->
                    kafkaProducer.send(sykmeldingStatusKafkaMessage, "syfoservice")
                }

                sykmeldingStatusConsumerService.start()

                count shouldEqual 100
            }
        }

        it("Should not handle envents from syfosmregister") {
            runBlocking {
                val events = 100
                val statusEvents = 0.until(events).map {
                    getSykmeldingKafkaMessage("from syfoservice", StatusEventDTO.SENDT)
                }

                var count = 0
                every { sykmeldingStatusService.registrerSendt(any()) } answers {
                    val sendtEvent = args[0] as SykmeldingSendEvent
                    sendtEvent.sykmeldingId shouldEqual "from syfoservice"
                    count++
                    applicationState.alive = false
                }
                val kafkaProducer = getKafkaProducer()
                statusEvents.forEach { sykmeldingStatusKafkaMessage ->
                    kafkaProducer.send(sykmeldingStatusKafkaMessage, "syfosmregister")
                }
                kafkaProducer.send(getSykmeldingKafkaMessage("from syfoservice", StatusEventDTO.SENDT), "syfoservice")
                sykmeldingStatusConsumerService.start()

                count shouldEqual 1
            }
        }
    }
})

fun getRandomStatus(): StatusEventDTO {
    return StatusEventDTO.values()[Random.nextInt(0, StatusEventDTO.values().size)]
}

fun getSykmeldingKafkaMessage(sykmeldingId: String, status: StatusEventDTO): SykmeldingStatusKafkaEvent {
    return when (status) {
        StatusEventDTO.SENDT -> SykmeldingStatusKafkaEvent(sykmeldingId, LocalDateTime.now(), StatusEventDTO.SENDT, getArbeidsgiverDTO(), listOf(SporsmalOgSvarDTO("arbeidsgiver", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Butikken")))
        StatusEventDTO.BEKREFTET -> SykmeldingStatusKafkaEvent(sykmeldingId, LocalDateTime.now(), StatusEventDTO.BEKREFTET, null, null)
        else -> SykmeldingStatusKafkaEvent(sykmeldingId, LocalDateTime.now(), status, null, null)
    }
}

private fun getArbeidsgiverDTO() = ArbeidsgiverDTO("123456780", "123456789", "testnavn")
