package no.nav.syfo.sykmelding.kafka.producer

import io.mockk.every
import io.mockk.mockkClass
import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmelding.kafka.util.JacksonNullableKafkaSerializer
import no.nav.syfo.testutil.KAFKA_IMAGE_NAME
import no.nav.syfo.testutil.KAFKA_IMAGE_VERSION
import no.nav.syfo.testutil.KafkaTestReader
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldNotBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

class BekreftSykmeldingKafkaProducerTest : Spek({
    val kafka = KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME).withTag(KAFKA_IMAGE_VERSION)).withNetwork(Network.newNetwork())
    kafka.start()
    fun setupKafkaConfig(): Properties {
        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonNullableKafkaSerializer::class.java
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        return kafkaConfig
    }
    val environment = mockkClass(Environment::class)
    every { environment.bekreftSykmeldingKafkaTopic } returns "syfo-bekreft-sykmelding"
    every { environment.applicationName } returns "application"
    every { environment.cluster } returns "localhost"

    val kafkaProducer = KafkaFactory.getBekreftetSykmeldingKafkaProducer(setupKafkaConfig(), environment)
    val properties = setupKafkaConfig().toConsumerConfig("${environment.applicationName}-consumer", JacksonKafkaDeserializer::class)
    properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
    val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
    val kafkaConsumer = KafkaConsumer<String, SykmeldingKafkaMessage>(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingKafkaMessage::class))
    kafkaConsumer.subscribe(listOf("syfo-bekreft-sykmelding"))

    afterGroup {
        kafka.stop()
    }

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
