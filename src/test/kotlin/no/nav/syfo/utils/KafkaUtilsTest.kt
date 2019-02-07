package no.nav.syfo.utils

import no.nav.syfo.ApplicationConfig
import no.nav.syfo.VaultSecrets
import org.apache.kafka.common.serialization.Serializer
import java.util.Properties
import kotlin.reflect.KClass

fun readProducerConfig(
    config: ApplicationConfig,
    secrets: VaultSecrets,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = valueSerializer
) = Properties().apply {
    load(ApplicationConfig::class.java.getResourceAsStream("/kafka_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${secrets.serviceuserUsername}\" password=\"${secrets.serviceuserPassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = config.kafkaBootstrapServers
}
