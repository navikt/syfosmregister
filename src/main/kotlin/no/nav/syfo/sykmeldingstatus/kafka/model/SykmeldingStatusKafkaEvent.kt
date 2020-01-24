package no.nav.syfo.sykmeldingstatus.kafka.model

import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO

data class SykmeldingStatusKafkaEvent(
    val sykmeldingId: String,
    val timestamp: LocalDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?
)

fun SykmeldingStatusEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, this.statusEvent, null, null)
}

fun SykmeldingSendEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, StatusEventDTO.SENDT, this.arbeidsgiver, this.sporsmalOgSvarListe)
}

fun SykmeldingBekreftEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, StatusEventDTO.BEKREFTET, null, this.sporsmalOgSvarListe)
}
