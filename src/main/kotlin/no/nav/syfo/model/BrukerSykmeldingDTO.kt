package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

data class BrukerSykmeldingDTO(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: ValidationResult,
    val legekontorOrgnummer: String,
    val legeNavn: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>
)

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

fun brukerSykmeldingFromResultSet(resultSet: ResultSet): BrukerSykmeldingDTO =
     BrukerSykmeldingDTO(
        id = resultSet.getString("id").trim(),
        bekreftetDato = resultSet.getTimestamp("bekreftet_dato")?.toLocalDateTime(),
        behandlingsutfall = objectMapper.readValue(resultSet.getString("behandlings_utfall")),
        legekontorOrgnummer = resultSet.getString("legekontor_org_nr").trim(),
        legeNavn = getLegenavn(resultSet),
        arbeidsgiver = arbeidsgiverTilArbeidsgiverDTO(objectMapper.readValue(resultSet.getString("arbeidsgiver"))),
        sykmeldingsperioder = getSyknmeldingsperioder(resultSet).map { periodeTilSykmeldingsperiodeDTO(it) }
    )

fun arbeidsgiverTilArbeidsgiverDTO(arbeidsgiver: Arbeidsgiver): ArbeidsgiverDTO? {
    return if (arbeidsgiver.harArbeidsgiver != HarArbeidsgiver.INGEN_ARBEIDSGIVER) {
        ArbeidsgiverDTO(
            navn = arbeidsgiver.navn!!,
            stillingsprosent = arbeidsgiver.stillingsprosent!!
        )
    } else null
}

fun periodeTilSykmeldingsperiodeDTO(periode: Periode): SykmeldingsperiodeDTO =
    SykmeldingsperiodeDTO(
        fom = periode.fom,
        tom = periode.tom,
        gradert = gradertTilGradertDTO(periode.gradert),
        behandlingsdager = periode.behandlingsdager,
        innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetypeDTO(periode)
    )

fun gradertTilGradertDTO(gradert: Gradert?): GradertDTO? =
        gradert?.let { GradertDTO(grad = it.grad, reisetilskudd = it.reisetilskudd) }

fun finnPeriodetypeDTO(periode: Periode): PeriodetypeDTO =
     when {
        periode.aktivitetIkkeMulig != null -> PeriodetypeDTO.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> PeriodetypeDTO.AVVENTENDE
        periode.behandlingsdager != null -> PeriodetypeDTO.BEHANDLINGSDAGER
        periode.gradert != null -> PeriodetypeDTO.GRADERT
        periode.reisetilskudd -> PeriodetypeDTO.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }


fun getSyknmeldingsperioder(resultSet: ResultSet): List<Periode> =
    objectMapper.readValue(resultSet.getString("perioder"))


fun getLegenavn(resultSet: ResultSet): String? {
    val fornavn = when (val value = resultSet.getString("lege_fornavn")) {
        null -> ""
        else -> value.plus(" ")
    }
    val mellomnavn = when (val value = resultSet.getString("lege_mellomnavn")) {
        null -> ""
        else -> value.plus(" ")
    }
    val etternavn = when (val value = resultSet.getString("lege_etternavn")) {
        null -> ""
        else -> value
    }
    val navn = "$fornavn$mellomnavn$etternavn"

    return if (navn.isBlank()) null else navn
}
