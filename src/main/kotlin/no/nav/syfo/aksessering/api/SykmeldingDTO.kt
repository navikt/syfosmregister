package no.nav.syfo.aksessering.api

import java.time.LocalDate
import java.time.LocalDateTime

data class SykmeldingDTO(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: BehandlingsutfallDTO,
    val legekontorOrgnummer: String,
    val legeNavn: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>
)

data class BehandlingsutfallDTO(
    val ruleHits: List<RegelinfoDTO>,
    val status: BehandlingsutfallStatusDTO
)

data class RegelinfoDTO(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String
)

enum class BehandlingsutfallStatusDTO {
    OK, MANUAL_PROCESSING, INVALID
}

data class ArbeidsgiverDTO(
    val navn: String,
    val stillingsprosent: Int
)

data class SykmeldingsperiodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: GradertDTO?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: PeriodetypeDTO
)

data class GradertDTO(
    val grad: Int,
    val reisetilskudd: Boolean
)

enum class PeriodetypeDTO {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}
