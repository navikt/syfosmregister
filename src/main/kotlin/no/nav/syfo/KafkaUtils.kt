package no.nav.syfo

import org.apache.kafka.common.serialization.Deserializer
import java.util.Properties
import kotlin.reflect.KClass

fun readConsumerConfig(
    config: ApplicationConfig,
    credentials: VaultCredentials,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = valueDeserializer
) = Properties().apply {
    load(ApplicationConfig::class.java.getResourceAsStream("/kafka_consumer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.deserializer"] = keyDeserializer.qualifiedName
    this["value.deserializer"] = valueDeserializer.qualifiedName
    this["bootstrap.servers"] = config.kafkaBootstrapServers
}
