package no.nav.syfo.sykmelding.internal.model

data class SporsmalDTO(
    val tekst: String,
    val shortName: ShortNameDTO,
    val svar: SvarDTO
)

enum class ShortNameDTO {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING
}

data class SvarDTO(
    val svarType: SvartypeDTO,
    val svar: String
)

enum class SvartypeDTO {
    ARBEIDSSITUASJON, PERIODER, JA_NEI }
