package no.nav.syfo.sykmelding.internal.model

import java.time.ZonedDateTime

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: ZonedDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmal: List<SporsmalDTO>
)
