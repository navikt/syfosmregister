package no.nav.syfo.sykmelding.kafka.producer

import io.mockk.every
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmelding.service.BehandligsutfallServiceTest
import no.nav.syfo.testutil.KafkaTest
import no.nav.syfo.testutil.KafkaTestReader
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test

class BekreftSykmeldingKafkaProducerTest {
    val environment = mockkClass(Environment::class)
    // TODO FIX THIS TEST

    @Test
    internal fun `Test kafka Should bekreft value to topic`() {
        every { environment.applicationName } returns
            "${
                BehandligsutfallServiceTest::
                class.simpleName
            }-application"
        every { environment.bekreftSykmeldingKafkaTopic } returns
            "${environment.applicationName}-syfo-bekreft-sykmelding"
        every { environment.cluster } returns "localhost"
        val kafkaconfig = KafkaTest.setupKafkaConfig()
        val kafkaProducer =
            KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment, kafkaconfig)
        val properties =
            kafkaconfig.toConsumerConfig(
                "${environment.applicationName}-consumer",
                JacksonKafkaDeserializer::class,
            )
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
        val kafkaConsumer =
            KafkaConsumer(
                properties,
                StringDeserializer(),
                JacksonKafkaDeserializer(SykmeldingKafkaMessage::class),
            )
        kafkaConsumer.subscribe(listOf("${environment.applicationName}-syfo-bekreft-sykmelding"))

        runBlocking {
            kafkaProducer.sendSykmelding(
                SykmeldingKafkaMessage(
                    getArbeidsgiverSykmelding("1"),
                    getKafkaMetadata("1"),
                    getSykmeldingStatusEvent("1"),
                ),
            )
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages["1"] shouldNotBe null
        }
    }

    @Test
    internal fun `Test kafka Should tombstone`() {
        every { environment.applicationName } returns
            "${
                BehandligsutfallServiceTest::
                class.simpleName
            }-application"
        every { environment.bekreftSykmeldingKafkaTopic } returns
            "${environment.applicationName}-syfo-bekreft-sykmelding"
        every { environment.cluster } returns "localhost"
        val kafkaconfig = KafkaTest.setupKafkaConfig()
        val kafkaProducer =
            KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment, kafkaconfig)
        val properties =
            kafkaconfig.toConsumerConfig(
                "${environment.applicationName}-consumer",
                JacksonKafkaDeserializer::class,
            )
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
        val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
        val kafkaConsumer =
            KafkaConsumer(
                properties,
                StringDeserializer(),
                JacksonKafkaDeserializer(SykmeldingKafkaMessage::class),
            )
        kafkaConsumer.subscribe(listOf("${environment.applicationName}-syfo-bekreft-sykmelding"))

        runBlocking {
            kafkaProducer.tombstoneSykmelding("1")
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages.containsKey("1") shouldBe true
            messages["1"] shouldBe null
        }
    }

    @Test
    internal fun `Test kafka should send Bekreft then tombstone`() {
        every { environment.applicationName } returns
            "${
                BehandligsutfallServiceTest::
                class.simpleName
            }-application"
        every { environment.bekreftSykmeldingKafkaTopic } returns
            "${environment.applicationName}-syfo-bekreft-sykmelding"
        every { environment.cluster } returns "localhost"
        val kafkaconfig = KafkaTest.setupKafkaConfig()
        val kafkaProducer =
            KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment, kafkaconfig)
        val properties =
            kafkaconfig.toConsumerConfig(
                "${environment.applicationName}-consumer",
                JacksonKafkaDeserializer::class,
            )
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
        val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
        val kafkaConsumer =
            KafkaConsumer(
                properties,
                StringDeserializer(),
                JacksonKafkaDeserializer(SykmeldingKafkaMessage::class),
            )
        kafkaConsumer.subscribe(listOf("${environment.applicationName}-syfo-bekreft-sykmelding"))

        runBlocking {
            kafkaProducer.sendSykmelding(
                SykmeldingKafkaMessage(
                    getArbeidsgiverSykmelding("2"),
                    getKafkaMetadata("2"),
                    getSykmeldingStatusEvent("2"),
                ),
            )
            kafkaProducer.tombstoneSykmelding("2")
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 2)
            messages.containsKey("2") shouldBe true
            messages["2"] shouldBe null
        }
    }
}
