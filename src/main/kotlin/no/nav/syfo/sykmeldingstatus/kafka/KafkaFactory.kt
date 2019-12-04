package no.nav.syfo.sykmeldingstatus.kafka

import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = kafkaBaseConfig.toProducerConfig(
                    "${environment.applicationName}-producer", JacksonKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessage>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
        }

        fun getSykmeldingStatusKafkaConsumer(kafkaBaseConfig: Properties, environment: Environment): SykmeldingStatusKafkaConsumer {
            val kafkaConsumerProperties = kafkaBaseConfig.toConsumerConfig(
                    "${environment.applicationName}-consumer", JacksonKafkaDeserializer::class
            )
            val kafkaConsumer = KafkaConsumer<String, SykmeldingStatusKafkaMessage>(kafkaConsumerProperties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessage::class))
            return SykmeldingStatusKafkaConsumer(kafkaConsumer, listOf(environment.sykmeldingStatusTopic))
        }
    }
}
