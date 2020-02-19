package no.nav.syfo.sykmelding.internal.model

import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import java.time.OffsetDateTime

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
