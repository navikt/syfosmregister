package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class MottattSykmeldingServiceTest : Spek({
    val testDb = TestDB()

    val environment = mockkClass(Environment::class)
    mockEnvironment(environment)
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val applicationState = ApplicationState(true, true)
    val consumerProperties = kafkaConfig.toConsumerConfig(
        "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )
    val producerProperties = kafkaConfig.toProducerConfig(
        "${environment.applicationName}-consumer", valueSerializer = JacksonKafkaSerializer::class
    )

    val receivedSykmeldingKafkaProducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val receivedSykmeldingKafkaConsumer = spyk(KafkaConsumer<String, String>(consumerProperties))
    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
    val tombstoneProducer = KafkaFactory.getTombstoneProducer(kafkaConfig, environment)
    val mottattSykmeldingStatusService = mockk<MottattSykmeldingStatusService>(relaxed = true)
    val mottattSykmeldingService = MottattSykmeldingService(
        applicationState = applicationState,
        kafkaconsumer = receivedSykmeldingKafkaConsumer,
        env = environment,
        database = testDb,
        mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
        sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
        mottattSykmeldingStatusService = mottattSykmeldingStatusService
    )

    afterEachTest {
        clearAllMocks()
        testDb.connection.dropData()
    }

    beforeEachTest {
        applicationState.ready = true
        mockEnvironment(environment)
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test receive sykmelding") {

        it("Handle resending to automatic topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.kafkaSm2013AutomaticDigitalHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.kafkaSm2013AutomaticDigitalHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            var first = true
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    if (!first) {
                        applicationState.ready = false
                    }
                    first = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }

            verify(exactly = 2) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
            verify(exactly = 1) { mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(receivedSykmelding.sykmelding.id, receivedSykmelding.personNrPasient) }
        }

        it("should receive sykmelding from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.kafkaSm2013AutomaticDigitalHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }
            val lagretSykmelding = testDb.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            verify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        it("should receive sykmelding med merknad from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding =
                getReceivedSykmelding(merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent")))
            receivedSykmeldingKafkaProducer.send(
                ProducerRecord(
                    environment.kafkaSm2013AutomaticDigitalHandlingTopic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            )
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }
            val merknader = testDb.connection.getMerknaderForId("1")
            merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
            merknader!![0].beskrivelse shouldBeEqualTo "ikke godkjent"
            verify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        it("should receive sykmelding from manuell topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.kafkaSm2013AutomaticDigitalHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }
            val lagretSykmelding = testDb.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            verify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        it("should receive sykmelding from avvist topic and not publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            receivedSykmeldingKafkaProducer.send(ProducerRecord(environment.sm2013InvalidHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }
            val lagretSykmelding = testDb.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            verify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        it("Should handle tombstone") {
            tombstoneProducer.tombstoneSykmelding(getReceivedSykmelding().sykmelding.id)
            every { receivedSykmeldingKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                mottattSykmeldingService.start()
            }
            val lagretSykmelding = testDb.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe false
            verify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }
})

private fun mockEnvironment(environment: Environment) {
    every { environment.applicationName } returns "${MottattSykmeldingServiceTest::class.simpleName}-application"
    every { environment.sm2013InvalidHandlingTopic } returns "${environment.applicationName}-invalidTopic"
    every { environment.sm2013ManualHandlingTopic } returns "${environment.applicationName}-manualTopic"
    every { environment.kafkaSm2013AutomaticDigitalHandlingTopic } returns "${environment.applicationName}-automaticTopic"
    every { environment.mottattSykmeldingKafkaTopic } returns "${environment.applicationName}-mottatttopic"
    every { environment.sykmeldingStatusAivenTopic } returns "${environment.applicationName}-statustopic"
    every { environment.sm2013BehandlingsUtfallTopic } returns "${environment.applicationName}-behandlingsutfall"
    every { environment.cluster } returns "localhost"
}
