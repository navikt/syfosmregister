package no.nav.syfo.sykmelding.internal.model

data class DiagnoseDTO(
    val kode: String,
    val system: String,
    val tekst: String?
)
