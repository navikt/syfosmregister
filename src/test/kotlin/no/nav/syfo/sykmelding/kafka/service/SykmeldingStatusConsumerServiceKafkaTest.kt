package no.nav.syfo.sykmelding.kafka.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.testutil.KafkaTest
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class SykmeldingStatusConsumerServiceKafkaTest : Spek({
    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "${SykmeldingStatusConsumerServiceKafkaTest::class.simpleName}"
    every { environment.sykmeldingStatusAivenTopic } returns "${environment.applicationName}-topic"
    every { environment.cluster } returns "localhost"
    val fnr = "12345678901"
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sendtSykmeldingKafkaProducer = mockkClass(SendtSykmeldingKafkaProducer::class)
    val bekreftSykmeldingKafkaProducer = mockkClass(BekreftSykmeldingKafkaProducer::class)
    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val tombstoneKafkaProducer = mockkClass(type = SykmeldingTombstoneProducer::class, relaxed = true)
    val databaseInterface = mockk<DatabaseInterface>(relaxed = true)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, mottattSykmeldingKafkaProducer, tombstoneKafkaProducer, databaseInterface)
    val consumer = spyk(KafkaFactory.getKafkaStatusConsumerAiven(kafkaConfig, environment))
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(consumer, applicationState, mottattSykmeldingStatusService)

    afterEachTest {
        clearAllMocks()
    }

    beforeEachTest {
        applicationState.alive = true
        applicationState.ready = true
        every { environment.applicationName } returns "${SykmeldingStatusConsumerServiceKafkaTest::class.simpleName}"
        every { environment.sykmeldingStatusAivenTopic } returns "${environment.applicationName}-topic"
        every { environment.cluster } returns "localhost"
        every { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
        every { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
        every { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(any()) } returns Unit
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
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
                    val timestamp = getNowTickMillisOffsetDateTime()
                    val sykmeldingApenEvent = SykmeldingStatusKafkaEventDTO(sykmelidngId, timestamp, STATUS_APEN)
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
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent =
                    SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, STATUS_APEN, null, null)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList()
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(sykmeldingId, timestamp, StatusEvent.APEN)
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("tombstone with APEN status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent =
                    SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, STATUS_APEN, null, null)
                every {
                    sykmeldingStatusService.getSykmeldingStatus(
                        any(),
                        any()
                    )
                } returns listOf(
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.BEKREFTET
                    )
                )
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(sykmeldingId, timestamp, StatusEvent.APEN)
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingId) }
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("test tombstone with apen -> bekreft -> apen") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent =
                    SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, STATUS_APEN, null, null)
                val sykmeldingBekreftEvent = SykmeldingStatusKafkaEventDTO(
                    sykmeldingId,
                    timestamp.plusSeconds(1),
                    STATUS_BEKREFTET,
                    null,
                    emptyList()
                )
                val sykmeldingApenEvent2 =
                    SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp.plusSeconds(2), STATUS_APEN, null, null)
                every { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns mockkClass(ArbeidsgiverSykmelding::class)
                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } returns Unit
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList() andThen listOf(
                    SykmeldingStatusEvent(sykmeldingId, getNowTickMillisOffsetDateTime(), StatusEvent.BEKREFTET)
                )
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

                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(
                    sykmeldingId,
                    timestamp.plusSeconds(2),
                    StatusEvent.APEN
                )
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingId) }
                verify(exactly = 2) { sykmeldingStatusService.registrerStatus(any()) }
            }
        }

        it("Test SENDT status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                var sykmeldingSendEvent: SykmeldingSendEvent? = null
                val sykmeldingSendKafkaEvent = SykmeldingStatusKafkaEventDTO(
                    sykmeldingId,
                    timestamp,
                    STATUS_SENDT,
                    ArbeidsgiverStatusDTO("1", "2", "navn"),
                    listOf(
                        SporsmalOgSvarDTO(
                            "tekst",
                            ShortNameDTO.ARBEIDSSITUASJON,
                            SvartypeDTO.ARBEIDSSITUASJON,
                            "svar"
                        )
                    )
                )
                every { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns mockkClass(ArbeidsgiverSykmelding::class)
                every {
                    sykmeldingStatusService.getSykmeldingStatus(
                        any(),
                        any()
                    )
                } returns listOf(
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.APEN
                    )
                )

                every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
                    sykmeldingSendEvent = args[0] as SykmeldingSendEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                    .send(sykmeldingSendKafkaEvent, fnr)
                sykmeldingStatusConsumerService.start()
                verify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { sykmeldingStatusService.registrerSendt(any(), any()) }
                sykmeldingSendEvent shouldBeEqualTo SykmeldingSendEvent(
                    sykmeldingId,
                    timestamp,
                    ArbeidsgiverStatus(sykmeldingId, "1", "2", "navn"),
                    Sporsmal(
                        "tekst",
                        ShortName.ARBEIDSSITUASJON,
                        Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "svar")
                    )
                )
                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(sykmeldingId, timestamp, StatusEvent.SENDT)
            }
        }

        it("Test SENDT -> SENDT") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val sykmeldingSendtKafkaEvent = SykmeldingStatusKafkaEventDTO(
                    sykmeldingId, getNowTickMillisOffsetDateTime(), STATUS_SENDT,
                    ArbeidsgiverStatusDTO("1", "2", "navn"),
                    listOf(SporsmalOgSvarDTO("sporsmal", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "svar"))
                )
                var counter = 0
                every { consumer.poll() } answers {
                    val cr = callOriginal()
                    if (cr.isNotEmpty()) {
                        counter++
                        if (counter > 1) {
                            applicationState.alive = false
                            applicationState.ready = false
                        }
                    }
                    cr
                }

                every { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns mockkClass(ArbeidsgiverSykmelding::class)
                every { sykmeldingStatusService.registrerSendt(any(), any()) } returns Unit
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList() andThen listOf(SykmeldingStatusEvent(sykmeldingId, getNowTickMillisOffsetDateTime(), StatusEvent.SENDT))

                val producer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                producer.send(sykmeldingSendtKafkaEvent, "fnr")
                producer.send(sykmeldingSendtKafkaEvent, "fnr")

                sykmeldingStatusConsumerService.start()
                verify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { sykmeldingStatusService.registrerSendt(any(), any()) }
                verify(exactly = 2) { sykmeldingStatusService.getSykmeldingStatus(any(), any()) }
            }
        }

        it("test AVBRUTT status") {
            runBlocking {
                val sykmeldingId = UUID.randomUUID().toString()
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                val sykmeldingApenEvent =
                    SykmeldingStatusKafkaEventDTO(sykmeldingId, timestamp, STATUS_AVBRUTT, null, null)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns emptyList()
                every { sykmeldingStatusService.registrerStatus(any()) } answers {
                    sykmeldingStatusEvent = args[0] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                kafkaProducer.send(sykmeldingApenEvent, fnr)

                sykmeldingStatusConsumerService.start()

                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(
                    sykmeldingId,
                    timestamp,
                    StatusEvent.AVBRUTT
                )
                verify(exactly = 1) { sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent!!) }
            }
        }

        it("Test BEREFTET status") {
            runBlocking {
                val sykmeldingId = "BEKREFT"
                val timestamp = getNowTickMillisOffsetDateTime()
                var sykmeldingStatusEvent: SykmeldingStatusEvent? = null
                var sykmeldingBekreftEvent: SykmeldingBekreftEvent? = null
                val sykmeldingBekreftKafkaEvent = SykmeldingStatusKafkaEventDTO(
                    sykmeldingId,
                    timestamp,
                    STATUS_BEKREFTET,
                    null,
                    emptyList()
                )
                every { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns mockkClass(ArbeidsgiverSykmelding::class)
                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
                    sykmeldingBekreftEvent = args[0] as SykmeldingBekreftEvent
                    sykmeldingStatusEvent = args[1] as SykmeldingStatusEvent
                    applicationState.alive = false
                    applicationState.ready = false
                }

                KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
                    .send(sykmeldingBekreftKafkaEvent, fnr)
                sykmeldingStatusConsumerService.start()

                verify(exactly = 1) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
                verify(exactly = 1) { sykmeldingStatusService.registrerBekreftet(any(), any()) }
                sykmeldingBekreftEvent shouldBeEqualTo SykmeldingBekreftEvent(
                    sykmeldingId,
                    timestamp,
                    emptyList()
                )
                sykmeldingStatusEvent shouldBeEqualTo SykmeldingStatusEvent(
                    sykmeldingId,
                    timestamp,
                    StatusEvent.BEKREFTET
                )
            }
        }
    }
})
