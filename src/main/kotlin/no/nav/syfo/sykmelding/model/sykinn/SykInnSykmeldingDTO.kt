package no.nav.syfo.sykmelding.model.sykinn

import java.time.LocalDate

data class SykInnSykmeldingDTO(
    val sykmeldingId: String,
    val periode: Periode,
    val pasient: Pasient,
    val hovedDiagnose: Diagnose
)

data class Periode(val fom: LocalDate, val tom: LocalDate)

data class Pasient(val fnr: String)

data class Diagnose(val code: String, val system: String, val text: String)
