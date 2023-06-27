package no.nav.syfo.testutil

import java.util.Properties
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmelding.kafka.util.JacksonNullableKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName

fun configureAndStartKafka(): KafkaContainer {
    val kafka =
        KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME).withTag(KAFKA_IMAGE_VERSION))
            .withNetwork(Network.newNetwork())
    kafka.start()
    return kafka
}

class KafkaTest {
    companion object {
        val kafka = configureAndStartKafka()

        fun setupKafkaConfig(): Properties {
            val kafkaConfig = Properties()
            kafkaConfig.let {
                it["bootstrap.servers"] = kafka.bootstrapServers
                it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
                it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
                it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
                    JacksonKafkaDeserializer::class.java
                it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =
                    JacksonNullableKafkaSerializer::class.java
                it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            }
            return kafkaConfig
        }
    }
}
