package no.nav.syfo.sykmeldingstatus.kafka.model

import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO

data class SendtSykmeldingKafkaMessage(
    val sykmelding: SendtSykmelding,
    val kafkaMetadataDTO: KafkaMetadataDTO,
    val sendtEvent: SykmeldingStatusKafkaEventDTO
)
