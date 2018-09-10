package no.nav.syfo

import kotlinx.coroutines.experimental.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.Properties
import kotlin.reflect.KClass

fun readConsumerConfig(
    env: Environment,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = valueDeserializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_consumer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.srvappnameUsername}\" password=\"${env.srvappnamePassword}\";"
    this["key.deserializer"] = keyDeserializer.qualifiedName
    this["value.deserializer"] = valueDeserializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}

fun readProducerConfig(
    env: Environment,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = valueSerializer
) = Properties().apply {
    load(Environment::class.java.getResourceAsStream("/kafka_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${env.srvappnameUsername}\" password=\"${env.srvappnamePassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = env.kafkaBootstrapServers
}

fun exampleProducer() {
    val env = Environment()

    val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)

    producer.send(ProducerRecord("aapen-test-topic", "test value"))
}

fun exampleConsumer(applicationState: ApplicationState) {
    val env = Environment()

    val consumerProperties = readConsumerConfig(env, valueDeserializer = StringDeserializer::class)
    launch {
        val consumer = KafkaConsumer<String, String>(consumerProperties)

        while (applicationState.running) {
            consumer.poll(Duration.ofMillis(1000)).forEach {
                println(it.value())
            }
        }
    }

}
