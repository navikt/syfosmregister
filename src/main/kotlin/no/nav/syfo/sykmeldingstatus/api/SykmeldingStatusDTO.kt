package no.nav.syfo.sykmeldingstatus.api

import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.StatusEventDTO

data class SykmeldingStatusDTO(
    val timestamp: LocalDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
)

data class SykmeldingSendEventDTO(
    val timestamp: LocalDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>
)

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgNavn: String
)

data class SykmeldingBekreftEventDTO(
    val timestamp: LocalDateTime,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
)

data class SporsmalOgSvarDTO(
    val tekst: String,
    val shortName: ShortNameDTO,
    val svartype: SvartypeDTO,
    val svar: String
)

enum class ShortNameDTO {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING
}

enum class SvartypeDTO {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI
}
