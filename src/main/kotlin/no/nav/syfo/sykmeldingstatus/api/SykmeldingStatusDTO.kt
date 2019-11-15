package no.nav.syfo.sykmeldingstatus.api

import java.time.LocalDateTime

data class SykmeldingSendEventDTO(
    val timestamp: LocalDateTime,
    val arbeidsgiver: ArbeidsgiverRTDTO,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>
)

data class ArbeidsgiverRTDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val orgNavn: String
)

data class SykmeldingBekreftEventDTO(
    val timestamp: LocalDateTime,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>
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
    PERIODE,
    PERIODER,
    JA_NEI
}
