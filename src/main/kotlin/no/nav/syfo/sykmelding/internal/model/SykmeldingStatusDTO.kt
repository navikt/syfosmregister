package no.nav.syfo.sykmelding.internal.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
