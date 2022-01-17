package no.nav.syfo.sykmelding.service

import io.mockk.every
import io.mockk.mockkClass
import io.mockk.spyk
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
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class BehandligsutfallServiceTest : Spek({
    val testDb = TestDB()
    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "application"
    every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
    every { environment.okSykmeldingTopic } returns "oksykmeldingtopic"
    every { environment.behandlingsUtfallTopic } returns "behandlingsutfallAiven"
    every { environment.avvistSykmeldingTopic } returns "avvisttopicAiven"
    every { environment.manuellSykmeldingTopic } returns "manuelltopic"
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
    val behandlingsutfallKafkaConsumerAiven = spyk(KafkaConsumer<String, String>(consumerProperties))
    val behandlingsutfallService = BehandlingsutfallService(
        applicationState = applicationState,
        database = testDb,
        env = environment,
        kafkaAivenConsumer = behandlingsutfallKafkaConsumerAiven
    )
    beforeEachTest {
        every { environment.applicationName } returns "application"
        every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
        every { environment.okSykmeldingTopic } returns "oksykmeldingtopic"
        every { environment.behandlingsUtfallTopic } returns "behandlingsutfallAiven"
        every { environment.avvistSykmeldingTopic } returns "avvisttopicAiven"
        every { environment.manuellSykmeldingTopic } returns "manuelltopic"
    }
    val tombstoneProducer = KafkaFactory.getTombstoneProducer(consumerProperties, environment)

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test BehandlingsuftallService") {
        it("Should read behandlingsutfall from topic and save in db") {
            val validationResult = ValidationResult(Status.OK, emptyList())
            behandlingsutfallKafkaProducer.send(
                ProducerRecord(
                    environment.behandlingsUtfallTopic,
                    "1",
                    validationResult
                )

            )
            every { behandlingsutfallKafkaConsumerAiven.poll(any<Duration>()) } answers {
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
            behandlingsutfall shouldBeEqualTo true
        }

        it("Should handle tombstone") {
            tombstoneProducer.tombstoneSykmelding("1")
            every { behandlingsutfallKafkaConsumerAiven.poll(any<Duration>()) } answers {
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
            behandlingsutfall shouldBeEqualTo false
        }
    }
})
