package no.nav.syfo.util

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsConfig

import java.util.Properties
import kotlin.reflect.KClass

fun loadBaseConfig(env: Environment, credentials: VaultSecrets): Properties = Properties().also {
    it.load(Environment::class.java.getResourceAsStream("/kafka_base.properties"))
    it["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    it["bootstrap.servers"] = env.kafkaBootstrapServers
}

fun Properties.envOverrides() = apply {
    putAll(System.getenv()
            .filter { (key, _) ->
                key.startsWith("KAFKA_")
            }
            .map { (key, value) ->
                key.substring(6).toLowerCase().replace("_", ".") to value
            }
            .toMap())
}

fun Properties.toConsumerConfig(
    groupId: String,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class
): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
    it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
    it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
}

fun Properties.toStreamsConfig(
    applicationName: String,
    valueSerde: KClass<out Serde<out Any>>,
    keySerde: KClass<out Serde<out Any>> = Serdes.String()::class
): Properties = Properties().also {
    it.putAll(this)
    it[StreamsConfig.APPLICATION_ID_CONFIG] = applicationName
    it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = keySerde.java
    it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = valueSerde.java
}

fun Properties.toProducerConfig(
    groupId: String,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
): Properties = Properties().also {
    it.putAll(this)
    it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
    it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
    it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
}
