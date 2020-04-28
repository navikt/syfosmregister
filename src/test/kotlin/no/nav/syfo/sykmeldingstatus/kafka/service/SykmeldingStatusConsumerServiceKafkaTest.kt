package no.nav.syfo.sykmeldingstatus.kafka.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
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
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory
import no.nav.syfo.sykmeldingstatus.kafka.model.SkjermetSykmelding
import no.nav.syfo.sykmeldingstatus.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.util.TimestampUtil.Companion.getAdjustedToLocalDateTime
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer

class SykmeldingStatusConsumerServiceKafkaTest : Spek({
    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "application"
    every { environment.sykmeldingStatusTopic } returns "topic"
    val fnr = "12345678901"
    val kafka = KafkaContainer()
    kafka.start()
    fun setupKafkaConfig(): Properties {
        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        return kafkaConfig
    }

    val kafkaConfig = setupKafkaConfig()
    var applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sendtSykmeldingKafkaProducer = mockkClass(SendtSykmeldingKafkaProducer::class)
    val bekreftSykmeldingKafkaProducer = mockkClass(BekreftSykmeldingKafkaProducer::class)
    val consumer = KafkaFactory.getKafkaStatusConsumer(kafkaConfig, environment)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusService, consumer, applicationState, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer)

    afterEachTest {
        clearAllMocks()
    }

    beforeEachTest {
        applicationState.alive = true
        applicationState.ready = true
        every { environment.applicationName } returns "application"
        every { environment.sykmeldingStatusTopic } returns "topic"
        every { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
        every { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
        every { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(any()) } returns Unit
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any()) } returns Unit
    }

    describe("Should retry on error") {
        it("Restart and continue from last offset") {
            runBlocking {
                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                val errors = 10
                val messages = 10
                var currentMessage = 0
                var currentError = 0
                0.until(messages).forEach {
                    val sykmelidngId = "" + it
                    val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    var sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmelidngId, timestamp, StatusEventDTO.APEN)
                    kafkaProducer.send(sykmeldingApenEvent, fnr)
                }
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList()
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    if (currentMessage == 5 && currentError < errors) {
                        currentError++
                        throw RuntimeException("ERROR")
                    }
                    currentMessage++
                    if (currentMessage >= messages) {
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                    Unit
                }
                sykmeldingStatusConsumerService.start()
                verify(exactly = messages + errors) { sykmeldingStatusService.registrerStatus(any()) }
            }
        }
    }
    describe("SykmeldingStatusConsumerService read from statustopic") {
        it("Test APEN status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, StatusEventDTO.APEN, null, null)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList()
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.APEN, timestamp)
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("tombstone with APEN status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, StatusEventDTO.APEN, null, null)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(SykmeldingStatusEvent(sykmeldingId, LocalDateTime.now(), StatusEvent.BEKREFTET))
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.APEN, timestamp)
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingId) }
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("test tombstone with apen -> bekreft -> apen") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, StatusEventDTO.APEN, null, null)
                val sykmeldingBekreftEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp.plusSeconds(1), StatusEventDTO.BEKREFTET, null, null)
                val sykmeldingApenEvent2 = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp.plusSeconds(2), StatusEventDTO.APEN, null, null)
                every { sykmeldingStatusService.getSkjermetSykmelding(any()) } returns mockkClass(SkjermetSykmelding::class)
                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } returns Unit
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList() andThen listOf(SykmeldingStatusEvent(sykmeldingId, LocalDateTime.now(), StatusEvent.BEKREFTET))
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    val lastBekreftet = sykmeldingStatusEvent?.event == StatusEvent.APEN
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    if (lastBekreftet && sykmeldingStatusEvent?.event == StatusEvent.APEN) {
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)
                kafkaProducer.send(sykmeldingBekreftEvent, fnr)
                kafkaProducer.send(sykmeldingApenEvent2, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp.plusSeconds(2)), StatusEvent.APEN, timestamp.plusSeconds(2))
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingId) }
                verify(exactly = 2) { sykmeldingStatusService.registrerStatus(any()) }
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

                every { sykmeldingStatusService.getSkjermetSykmelding(any()) } returns mockkClass(SkjermetSykmelding::class)
                every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
                    sykmeldingSendEvent = args[0] as SykmeldingSendEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment).send(sykmeldingSendKafkaEvent, fnr)
                sykmeldingStatusConsumerService.start()
                verify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { sykmeldingStatusService.registrerSendt(any(), any()) }
                sykmeldingSendEvent shouldEqual SykmeldingSendEvent(
                        sykmeldingId,
                        getAdjustedToLocalDateTime(timestamp),
                        ArbeidsgiverStatus(sykmeldingId, "1", "2", "navn"),
                        Sporsmal("tekst", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "svar")))
                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.SENDT, timestamp)
            }
        }

        it("test AVBRUTT status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, StatusEventDTO.AVBRUTT, null, null)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList()
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldEqual SykmeldingStatusEvent(sykmeldingId, getAdjustedToLocalDateTime(timestamp), StatusEvent.AVBRUTT, timestamp)
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("Test BEREFTET status") {
            runBlocking {
                val sykmeldingId = "BEKREFT"
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
                every { sykmeldingStatusService.getSkjermetSykmelding(any()) } returns mockkClass(SkjermetSykmelding::class)
                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
                    sykmeldingBekreftEvent = args[0] as SykmeldingBekreftEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment).send(sykmeldingBekreftKafkaEvent, fnr)
                sykmeldingStatusConsumerService.start()

                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
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
