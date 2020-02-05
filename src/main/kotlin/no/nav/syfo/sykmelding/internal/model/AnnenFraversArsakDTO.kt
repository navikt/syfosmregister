package no.nav.syfo.sykmelding.internal.model

data class AnnenFraversArsakDTO(
    val beskrivelse: String?,
    val grunn: List<AnnenFraverGrunnDTO>
)
