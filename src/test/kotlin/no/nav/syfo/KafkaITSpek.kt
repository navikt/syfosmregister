package no.nav.syfo

import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.testutil.getRandomPort
import no.nav.syfo.testutil.getVaultSecrets
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topics = listOf(topic)
    )

    val credentials = getVaultSecrets()
    val env = Environment(
        applicationPort = getRandomPort(),
        kafkaSm2013AutomaticPapirmottakTopic = "topic1",
        kafkaSm2013AutomaticDigitalHandlingTopic = "topic3",
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        syfosmregisterDBURL = "12314.adeo.no",
        mountPathVault = "vault.adeo.no",
        cluster = "local",
        databaseName = "syfosmregister",
        applicationName = "syfosmregister",
        sm2013ManualHandlingTopic = "topic2",
        jwtIssuer = "",
        appIds = listOf("123"),
        clientId = "",
        jwtIssuerServiceuser = "issuer"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(env, credentials).overrideForTest()

    val producerProperties = baseConfig
            .toProducerConfig("spek.integration", valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)

    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)

    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
