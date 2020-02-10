package no.nav.syfo.sykmelding.internal.model

import java.time.ZonedDateTime
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: ZonedDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)
