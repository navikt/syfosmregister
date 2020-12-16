package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.verify
import java.time.Duration
import java.util.Properties
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.testutil.KAFKA_IMAGE_NAME
import no.nav.syfo.testutil.KAFKA_IMAGE_VERSION
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

class MottattSykmeldingServiceTest : Spek({
    val testDb = TestDB()
    val kafka = KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME).withTag(KAFKA_IMAGE_VERSION)).withNetwork(Network.newNetwork())
    kafka.start()
    val environment = mockkClass(Environment::class)
    mockEnvironment(environment)
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
    val mottattSykmeldingService = MottattSykmeldingService(
            applicationState = applicationState,
            kafkaconsumer = receivedSykmeldingKafkaConsumer,
            env = environment,
            database = testDb,
            mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
            sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer
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
        kafka.stop()
    }

    describe("Test receive sykmelding") {
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
    every { environment.applicationName } returns "application"
    every { environment.sm2013InvalidHandlingTopic } returns "invalidTopic"
    every { environment.sm2013ManualHandlingTopic } returns "manualTopic"
    every { environment.kafkaSm2013AutomaticDigitalHandlingTopic } returns "automaticTopic"
    every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
    every { environment.sykmeldingStatusTopic } returns "statustopic"
    every { environment.sm2013BehandlingsUtfallTopic } returns "behandlingsutfall"
    every { environment.cluster } returns "localhost"
}
