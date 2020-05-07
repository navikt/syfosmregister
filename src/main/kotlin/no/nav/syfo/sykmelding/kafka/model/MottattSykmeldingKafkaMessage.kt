package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO

data class MottattSykmeldingKafkaMessage(
    val sykmelding: EnkelSykmelding,
    val kafkaMetadata: KafkaMetadataDTO
)
