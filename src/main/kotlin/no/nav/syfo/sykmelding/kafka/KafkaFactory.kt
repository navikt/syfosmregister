package no.nav.syfo.sykmelding.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
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

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(
            environment: Environment,
            kafkaConfig: Properties = KafkaUtils.getAivenKafkaConfig("status-sykmelding-producer"),
        ): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig =
                kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-gcp-producer",
                    JacksonKafkaSerializer::class,
                )
            val kafkaProducer =
                KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(
                kafkaProducer,
                environment.sykmeldingStatusAivenTopic
            )
        }

        fun getKafkaStatusConsumerAiven(
            environment: Environment,
            kafkaConfig: Properties =
                KafkaUtils.getAivenKafkaConfig("status-consumer").also {
                    it.let {
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    }
                },
        ): SykmeldingStatusKafkaConsumer {
            val properties =
                kafkaConfig.toConsumerConfig(
                    "${environment.applicationName}-gcp-consumer",
                    JacksonKafkaDeserializer::class
                )
            val kafkaConsumer =
                KafkaConsumer(
                    properties,
                    StringDeserializer(),
                    JacksonKafkaDeserializer(SykmeldingStatusKafkaMessageDTO::class)
                )
            return SykmeldingStatusKafkaConsumer(
                kafkaConsumer,
                listOf(environment.sykmeldingStatusAivenTopic)
            )
        }

        fun getSendtSykmeldingKafkaProducer(
            environment: Environment,
            kafkaConfig: Properties = KafkaUtils.getAivenKafkaConfig("sendt-sykmelding-producer"),
        ): SendtSykmeldingKafkaProducer {
            val kafkaProducerProperties =
                kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-gcp-producer",
                    JacksonNullableKafkaSerializer::class,
                )
            val kafkaProducer =
                KafkaProducer<String, SykmeldingKafkaMessage?>(kafkaProducerProperties)
            return SendtSykmeldingKafkaProducer(kafkaProducer, environment.sendSykmeldingKafkaTopic)
        }

        fun getBekreftetSykmeldingKafkaProducer(
            environment: Environment,
            kafkaConfig: Properties =
                KafkaUtils.getAivenKafkaConfig("bekreftet-sykmelding-producer"),
        ): BekreftSykmeldingKafkaProducer {
            val kafkaProducerProperties =
                kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-gcp-producer",
                    JacksonNullableKafkaSerializer::class,
                )
            val kafkaProducer =
                KafkaProducer<String, SykmeldingKafkaMessage?>(kafkaProducerProperties)
            return BekreftSykmeldingKafkaProducer(
                kafkaProducer,
                environment.bekreftSykmeldingKafkaTopic
            )
        }

        fun getMottattSykmeldingKafkaProducer(
            environment: Environment,
            kafkaConfig: Properties = KafkaUtils.getAivenKafkaConfig("mottatt-sykmelding-producer"),
        ): MottattSykmeldingKafkaProducer {
            val kafkaProducerProperties =
                kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-gcp-producer",
                    JacksonNullableKafkaSerializer::class,
                )
            val kafkaProducer =
                KafkaProducer<String, MottattSykmeldingKafkaMessage?>(kafkaProducerProperties)
            return MottattSykmeldingKafkaProducer(
                kafkaProducer,
                environment.mottattSykmeldingKafkaTopic
            )
        }

        fun getTombstoneProducer(
            environment: Environment,
            kafkaConfig: Properties = KafkaUtils.getAivenKafkaConfig("tombstone-producer"),
        ): SykmeldingTombstoneProducer {
            val kafkaProducerProperties =
                kafkaConfig.toProducerConfig(
                    "${environment.applicationName}-gcp-producer",
                    JacksonNullableKafkaSerializer::class,
                )
            val kafkaProducer = KafkaProducer<String, Any?>(kafkaProducerProperties)
            return SykmeldingTombstoneProducer(
                kafkaProducer,
                listOf(
                    environment.okSykmeldingTopic,
                    environment.manuellSykmeldingTopic,
                    environment.avvistSykmeldingTopic,
                    environment.behandlingsUtfallTopic,
                    environment.mottattSykmeldingKafkaTopic,
                ),
            )
        }

        fun getKafkaConsumerAivenPdlAktor(
            environment: Environment,
            kafkaConfig: Properties = KafkaUtils.getAivenKafkaConfig("pdl-aktor-consumer"),
        ): KafkaConsumer<String, GenericRecord> {
            val consumerProperties =
                kafkaConfig
                    .apply {
                        setProperty(
                            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                            environment.schemaRegistryUrl
                        )
                        setProperty(
                            KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                            "${environment.kafkaSchemaRegistryUsername}:${environment.kafkaSchemaRegistryPassword}"
                        )
                        setProperty(
                            KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE,
                            "USER_INFO"
                        )
                    }
                    .toConsumerConfig(
                        "${environment.applicationName}-gcp-consumer",
                        valueDeserializer = KafkaAvroDeserializer::class,
                    )
                    .also {
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                        it["specific.avro.reader"] = false
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                    }

            return KafkaConsumer<String, GenericRecord>(consumerProperties)
        }
    }
}
