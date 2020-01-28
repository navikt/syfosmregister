package no.nav.syfo.sykmelding.internal.model

data class SporsmalSvarDTO(
    val sporsmal: String,
    val svar: String,
    val restriksjoner: List<SvarRestriksjonDTO>
)
