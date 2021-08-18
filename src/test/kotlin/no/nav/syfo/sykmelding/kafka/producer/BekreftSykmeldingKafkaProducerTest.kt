package no.nav.syfo.sykmelding.kafka.producer

import io.mockk.every
import io.mockk.mockkClass
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BekreftSykmeldingKafkaProducerTest : Spek({

    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "${BehandligsutfallServiceTest::class.simpleName}-application"
    every { environment.bekreftSykmeldingKafkaTopic } returns "${environment.applicationName}-syfo-bekreft-sykmelding"
    every { environment.cluster } returns "localhost"
    val kafkaconfig = KafkaTest.setupKafkaConfig()
    val kafkaProducer = KafkaFactory.getBekreftetSykmeldingKafkaProducer(kafkaconfig, environment)
    val properties = kafkaconfig.toConsumerConfig("${environment.applicationName}-consumer", JacksonKafkaDeserializer::class)
    properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
    val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
    val kafkaConsumer = KafkaConsumer<String, SykmeldingKafkaMessage>(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingKafkaMessage::class))
    kafkaConsumer.subscribe(listOf("${environment.applicationName}-syfo-bekreft-sykmelding"))

    describe("Test kafka") {
        it("Should bekreft value to topic") {
            kafkaProducer.sendSykmelding(SykmeldingKafkaMessage(getEnkelSykmelding("1"), getKafkaMetadata("1"), getSykmeldingStatusEvent("1")))
            var messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages.get("1") shouldNotBe null
        }

        it("Should tombstone") {

            kafkaProducer.tombstoneSykmelding("1")
            var messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages.containsKey("1") shouldBe true
            messages.get("1") shouldBe null
        }

        it("should send Bekreft then tombstone") {
            kafkaProducer.sendSykmelding(SykmeldingKafkaMessage(getEnkelSykmelding("2"), getKafkaMetadata("2"), getSykmeldingStatusEvent("2")))
            kafkaProducer.tombstoneSykmelding("2")
            var messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 2)
            messages.containsKey("2") shouldBe true
            messages.get("2") shouldBe null
        }
    }
})
