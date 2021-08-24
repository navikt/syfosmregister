package no.nav.syfo.sykmelding.service

import io.mockk.every
import io.mockk.mockkClass
import io.mockk.spyk
import java.time.Duration
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.erBehandlingsutfallLagret
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.testutil.KafkaTest
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BehandligsutfallServiceTest : Spek({
    val testDb = TestDB()
    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "${BehandligsutfallServiceTest::class.simpleName}"
    every { environment.sm2013InvalidHandlingTopic } returns "${environment.applicationName}-invalidTopic"
    every { environment.sm2013ManualHandlingTopic } returns "${environment.applicationName}-manualTopic"
    every { environment.kafkaSm2013AutomaticDigitalHandlingTopic } returns "${environment.applicationName}-automaticTopic"
    every { environment.mottattSykmeldingKafkaTopic } returns "${environment.applicationName}-mottatttopic"
    every { environment.sykmeldingStatusTopic } returns "${environment.applicationName}-statustopic"
    every { environment.sm2013BehandlingsUtfallTopic } returns "${environment.applicationName}-behandlingsutfall"
    every { environment.cluster } returns "localhost"
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val applicationState = ApplicationState(true, true)
    val consumerProperties = kafkaConfig.toConsumerConfig(
            "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )
    val producerProperties = kafkaConfig.toProducerConfig(
            "${environment.applicationName}-consumer", valueSerializer = JacksonKafkaSerializer::class
    )
    val behandlingsutfallKafkaProducer = KafkaProducer<String, ValidationResult>(producerProperties)
    val behandlingsutfallKafkaConsumer = spyk(KafkaConsumer<String, String>(consumerProperties))
    val behandlingsutfallService = BehandlingsutfallService(
            applicationState = applicationState,
            database = testDb,
            env = environment,
            kafkaconsumer = behandlingsutfallKafkaConsumer
    )

    val tombstoneProducer = KafkaFactory.getTombstoneProducer(consumerProperties, environment)
    beforeEachTest {
        every { environment.applicationName } returns "application"
        every { environment.sm2013InvalidHandlingTopic } returns "invalidTopic"
        every { environment.sm2013ManualHandlingTopic } returns "manualTopic"
        every { environment.kafkaSm2013AutomaticDigitalHandlingTopic } returns "automaticTopic"
        every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
        every { environment.sykmeldingStatusTopic } returns "statustopic"
        every { environment.sm2013BehandlingsUtfallTopic } returns "behandlingsutfall"
    }

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test BehandlingsuftallService") {
        it("Should read behandlingsutfall from topic and save in db") {
            val validationResult = ValidationResult(Status.OK, emptyList())
            behandlingsutfallKafkaProducer.send(ProducerRecord(environment.sm2013BehandlingsUtfallTopic, "1", validationResult))
            every { behandlingsutfallKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                behandlingsutfallService.start()
            }

            val behandlingsutfall = testDb.connection.erBehandlingsutfallLagret("1")
            behandlingsutfall shouldEqual true
        }

        it("Should handle tombstone") {
            tombstoneProducer.tombstoneSykmelding("1")
            every { behandlingsutfallKafkaConsumer.poll(any<Duration>()) } answers {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }
            runBlocking {
                behandlingsutfallService.start()
            }

            val behandlingsutfall = testDb.connection.erBehandlingsutfallLagret("1")
            behandlingsutfall shouldEqual false
        }
    }
})
