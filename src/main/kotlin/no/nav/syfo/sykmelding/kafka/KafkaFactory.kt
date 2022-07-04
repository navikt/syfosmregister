package no.nav.syfo.sykmelding.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import no.nav.syfo.Environment
import no.nav.syfo.Serviceuser
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
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
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = kafkaBaseConfig.toProducerConfig(
                "${environment.applicationName}-producer", JacksonKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusAivenTopic)
        }

        fun getKafkaStatusConsumerAiven(kafkaConfig: Properties, environment: Environment): SykmeldingStatusKafkaConsumer {
            val properties = kafkaConfig.toConsumerConfig("${environment.applicationName}-consumer", JacksonKafkaDeserializer::class)
            val kafkaConsumer = KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(properties, StringDeserializer(), JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class))
            return SykmeldingStatusKafkaConsumer(kafkaConsumer, listOf(environment.sykmeldingStatusAivenTopic))
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
            return SykmeldingTombstoneProducer(
                kafkaProducer,
                listOf(
                    environment.okSykmeldingTopic,
                    environment.manuellSykmeldingTopic,
                    environment.avvistSykmeldingTopic,
                    environment.behandlingsUtfallTopic,
                    environment.mottattSykmeldingKafkaTopic
                )
            )
        }

        fun getKafkaConsumerPdlAktor(vaultServiceUser: Serviceuser, environment: Environment): KafkaConsumer<String, GenericRecord> {
            val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceUser)
                .also {
                    it["auto.offset.reset"] = "latest"
                    it["specific.avro.reader"] = false
                }
                .envOverrides()
            val properties = kafkaBaseConfig.toConsumerConfig("${environment.applicationName}-consumer", KafkaAvroDeserializer::class)
            properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

            return KafkaConsumer<String, GenericRecord>(properties)
        }
    }
}
