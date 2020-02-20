package no.nav.syfo.sykmeldingstatus.kafka.model

import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.tilArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.tilSporsmalOgSvarDTOListe
import no.nav.syfo.sykmeldingstatus.api.toStatusEventDTO

data class SykmeldingStatusKafkaEvent(
    val sykmeldingId: String,
    val timestamp: LocalDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?
)

fun SykmeldingStatusEvent.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, this.event.toStatusEventDTO(), null, null)
}

fun SykmeldingSendEvent.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, StatusEventDTO.SENDT, tilArbeidsgiverStatusDTO(this.arbeidsgiver), tilSporsmalOgSvarDTOListe(listOf(this.sporsmal)))
}

fun SykmeldingBekreftEvent.toSykmeldingStatusKafkaEvent(sykmeldingId: String): SykmeldingStatusKafkaEvent {
    return SykmeldingStatusKafkaEvent(sykmeldingId, this.timestamp, StatusEventDTO.BEKREFTET, null, tilSporsmalOgSvarDTOListe(this.sporsmal))
}
