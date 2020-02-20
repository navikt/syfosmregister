package no.nav.syfo.sykmeldingstatus.kafka.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.ArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.util.TimestampUtil.Companion.getAdjustedToLocalDateTime
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
    var applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    fun getKafkaConsumer(): KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO> {
        return KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(kafkaConfig, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class))
    }

    fun getKafkaProducer(): SykmeldingStatusKafkaProducer {
        return SykmeldingStatusKafkaProducer(KafkaProducer(kafkaConfig), "topic")
    }
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusService, SykmeldingStatusKafkaConsumer(getKafkaConsumer(), listOf("topic")), applicationState)

    afterGroup {
        kafka.stop()
    }

    beforeEachTest { clearAllMocks(); applicationState.alive = true; }

    describe("SykmeldingStatusConsumerService read from statustopic") {
        it("Test APEN status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, StatusEventDTO.APEN, null, null)
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                val kafkaProducer = getKafkaProducer()
                kafkaProducer.send(sykmeldingApenEvent)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.APEN, timestamp)
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("Test SENDT status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                var sykmeldingSendEvent: SykmeldingSendEvent? = null
                val sykmeldingSendKafkaEvent = SykmeldingStatusKafkaEventDTO(
                        sykmeldingId,
                        timestamp,
                        StatusEventDTO.SENDT,
                        ArbeidsgiverStatusDTO("1", "2", "navn"),
                        listOf(SporsmalOgSvarDTO("tekst", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "svar")))
                every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
                    sykmeldingSendEvent = args[0] as SykmeldingSendEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(sykmeldingSendKafkaEvent)
                sykmeldingStatusConsumerService.start()

                verify(exactly = 1) { sykmeldingStatusService.registrerSendt(any(), any()) }
                sykmeldingSendEvent shouldEqual SykmeldingSendEvent(
                        sykmeldingId,
                        getAdjustedToLocalDateTime(timestamp),
                        ArbeidsgiverStatus(sykmeldingId, "1", "2", "navn"),
                        Sporsmal("tekst", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "svar")))
                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.SENDT, timestamp)
            }
        }

        it("Test BEREFTET status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                var sykmeldingBekreftEvent: SykmeldingBekreftEvent? = null
                val sykmeldingBekreftKafkaEvent = SykmeldingStatusKafkaEventDTO(
                        sykmeldingId,
                        timestamp,
                        StatusEventDTO.BEKREFTET,
                        null,
                        null
                )

                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
                    sykmeldingBekreftEvent = args[0] as SykmeldingBekreftEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                }

                getKafkaProducer().send(sykmeldingBekreftKafkaEvent)
                sykmeldingStatusConsumerService.start()

                verify(exactly = 1) { sykmeldingStatusService.registrerBekreftet(any(), any()) }
                sykmeldingBekreftEvent shouldEqual SykmeldingBekreftEvent(
                        sykmeldingId,
                        getAdjustedToLocalDateTime(timestamp),
                        null
                )
                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(
                        sykmeldingId,
                        getAdjustedToLocalDateTime(timestamp),
                        StatusEvent.BEKREFTET,
                        timestamp
                )
            }
        }
    }
})
