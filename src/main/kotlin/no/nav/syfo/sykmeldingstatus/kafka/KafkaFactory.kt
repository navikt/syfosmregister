package no.nav.syfo.sykmeldingstatus.kafka

import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmeldingstatus.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = kafkaBaseConfig.toProducerConfig(
                    "${environment.applicationName}-producer", JacksonKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
        }

        fun getKafkaStatusConsumer(kafkaConfig: Properties, environment: Environment): SykmeldingStatusKafkaConsumer {
            val properties = kafkaConfig.toConsumerConfig("${environment.applicationName}-consumer", JacksonKafkaDeserializer::class)
            properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
            val kafkaConsumer = KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class))
            return SykmeldingStatusKafkaConsumer(kafkaConsumer, listOf(environment.sykmeldingStatusTopic))
        }

        fun getSendtSykmeldingKafkaProducer(kafkaConfig: Properties, environment: Environment): SendtSykmeldingKafkaProducer {
            val kafkaProducerProperties = kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-producer",
                    JacksonKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SendtSykmeldingKafkaMessage>(kafkaProducerProperties)
            return SendtSykmeldingKafkaProducer(kafkaProducer, environment.sendSykmeldingKafkaTopic)
        }
    }
}
