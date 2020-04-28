package no.nav.syfo.sykmeldingstatus.kafka.model

import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO

data class SykmeldingKafkaMessage(
    val sykmelding: SkjermetSykmelding,
    val kafkaMetadata: KafkaMetadataDTO,
    val event: SykmeldingStatusKafkaEventDTO
)
