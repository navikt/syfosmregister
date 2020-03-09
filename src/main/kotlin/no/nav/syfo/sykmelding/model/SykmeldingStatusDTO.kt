package no.nav.syfo.sykmelding.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
