package no.nav.syfo.sykmeldingstatus.kafka.model

data class SykmeldingStatusKafkaMessage(
    val kafkaMetadata: KafkaMetadata,
    val event: SykmeldingStatusKafkaEvent
)
