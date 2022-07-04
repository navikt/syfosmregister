package no.nav.syfo.sykmelding.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.log
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.testutil.KafkaTest
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getMerknaderForId
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration

class MottattSykmeldingServiceTest : FunSpec({
    val database = TestDB.database

    val environment = mockkClass(Environment::class)
    mockEnvironment(environment)
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val applicationState = ApplicationState(true, true)
    val producerProperties = kafkaConfig.toProducerConfig(
        "${environment.applicationName}-producer", valueSerializer = JacksonKafkaSerializer::class
    )
    val consumerPropertiesAiven = kafkaConfig.toConsumerConfig(
        "${environment.applicationName}-aiven-consumer", valueDeserializer = StringDeserializer::class
    )
    val receivedSykmeldingKafkaProducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val receivedSykmeldingKafkaConsumerAiven = spyk(KafkaConsumer<String, String>(consumerPropertiesAiven))
    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
    val tombstoneProducer = KafkaFactory.getTombstoneProducer(kafkaConfig, environment)
    val mottattSykmeldingStatusService = mockk<MottattSykmeldingStatusService>(relaxed = true)
    val mottattSykmeldingService = MottattSykmeldingService(
        applicationState = applicationState,
        kafkaAivenConsumer = receivedSykmeldingKafkaConsumerAiven,
        env = environment,
        database = database,
        mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
        sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
        mottattSykmeldingStatusService = mottattSykmeldingStatusService
    )

    afterTest {
        clearAllMocks()
        database.connection.dropData()
    }

    beforeTest {
        applicationState.ready = true
        mockEnvironment(environment)
    }

    afterSpec {
        TestDB.stop()
    }

    context("Test receive sykmelding") {
        test("Handle resending to automatic topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            var first = true
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    if (!first) {
                        applicationState.ready = false
                    }
                    first = false
                }
                cr
            }

            mottattSykmeldingService.start()

            coVerify(exactly = 2) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
            coVerify(exactly = 1) { mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(receivedSykmelding.sykmelding.id, receivedSykmelding.personNrPasient) }
        }

        test("should receive sykmelding from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }

            mottattSykmeldingService.start()

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        test("should receive sykmelding med merknad from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding =
                getReceivedSykmelding(merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent")))
            receivedSykmeldingKafkaProducer.send(
                ProducerRecord(
                    environment.okSykmeldingTopic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            )
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }

            mottattSykmeldingService.start()

            val merknader = database.connection.getMerknaderForId("1")
            merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
            merknader[0].beskrivelse shouldBeEqualTo "ikke godkjent"
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        test("should receive sykmelding from manuell topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }

            mottattSykmeldingService.start()

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        test("should receive sykmelding from avvist topic and not publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.avvistSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }

            mottattSykmeldingService.start()

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        test("Should handle tombstone") {
            tombstoneProducer.tombstoneSykmelding(getReceivedSykmelding().sykmelding.id)
            every { receivedSykmeldingKafkaConsumerAiven.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                log.info("polling")
                if (!cr.isEmpty) {
                    log.info("not-empty")
                    applicationState.ready = false
                }
                cr
            }

            mottattSykmeldingService.start()

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe false
            coVerify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }
})

private fun mockEnvironment(environment: Environment) {
    every { environment.applicationName } returns "${MottattSykmeldingServiceTest::class.simpleName}-application"
    every { environment.mottattSykmeldingKafkaTopic } returns "${environment.applicationName}-mottatttopic"
    every { environment.sykmeldingStatusAivenTopic } returns "${environment.applicationName}-statustopic"
    every { environment.okSykmeldingTopic } returns "${environment.applicationName}-oksykmeldingtopic"
    every { environment.behandlingsUtfallTopic } returns "${environment.applicationName}-behandlingsutfallAiven"
    every { environment.avvistSykmeldingTopic } returns "${environment.applicationName}-avvisttopiclAiven"
    every { environment.manuellSykmeldingTopic } returns "${environment.applicationName}-manuelltopic"
    every { environment.cluster } returns "localhost"
}
