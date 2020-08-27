package no.nav.syfo.sykmelding.kafka

import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.sykmelding.kafka.util.JacksonNullableKafkaSerializer
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
                    JacksonNullableKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingKafkaMessage>(kafkaProducerProperties)
            return SendtSykmeldingKafkaProducer(kafkaProducer, environment.sendSykmeldingKafkaTopic)
        }
        fun getBekreftetSykmeldingKafkaProducer(kafkaConfig: Properties, environment: Environment): BekreftSykmeldingKafkaProducer {
            val kafkaProducerProperties = kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-producer",
                    JacksonNullableKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingKafkaMessage?>(kafkaProducerProperties)
            return BekreftSykmeldingKafkaProducer(kafkaProducer, environment.bekreftSykmeldingKafkaTopic)
        }

        fun getMottattSykmeldingKafkaProducer(kafkaConfig: Properties, environment: Environment): MottattSykmeldingKafkaProducer {
            val kafkaProducerProperties = kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-producer",
                    JacksonNullableKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, MottattSykmeldingKafkaMessage?>(kafkaProducerProperties)
            return MottattSykmeldingKafkaProducer(kafkaProducer, environment.mottattSykmeldingKafkaTopic)
        }

        fun getTombstoneProducer(kafkaConfig: Properties, environment: Environment): SykmeldingTombstoneProducer {
            val kafkaProducerProperties = kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-producer",
                    JacksonNullableKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, Any?>(kafkaProducerProperties)
            return SykmeldingTombstoneProducer(kafkaProducer, listOf(
                    environment.kafkaSm2013AutomaticDigitalHandlingTopic,
                    environment.sm2013ManualHandlingTopic,
                    environment.sm2013InvalidHandlingTopic,
                    environment.mottattSykmeldingKafkaTopic,
                    environment.sm2013BehandlingsUtfallTopic,
                    environment.sykmeldingStatusTopic)
            )
        }
    }
}
