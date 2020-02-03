package no.nav.syfo.sykmelding.internal.model

data class MedisinskVurderingDTO(
    val hovedDiagnose: DiagnoseDTO?,
    val biDiagnoser: List<DiagnoseDTO>
)
