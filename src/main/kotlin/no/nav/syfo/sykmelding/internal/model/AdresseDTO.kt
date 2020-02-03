package no.nav.syfo.sykmelding.internal.model

data class AdresseDTO(
    val gate: String?,
    val postnummer: Int?,
    val kommune: String?,
    val postboks: String?,
    val land: String?
)
