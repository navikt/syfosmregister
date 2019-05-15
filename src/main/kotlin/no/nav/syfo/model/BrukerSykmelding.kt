package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

data class BrukerSykmelding(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: ValidationResult,
    val legekontorOrgnummer: String,
    val legeNavn: String?,
    val arbeidsgiverNavn: String,
    val sykmeldingsperioder: List<Sykmeldingsperiode>
)

data class Sykmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int?,
    val type: Periodetype
)

enum class Periodetype {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

fun brukerSykmeldingFromResultSet(resultSet: ResultSet): BrukerSykmelding {
    return BrukerSykmelding(
        id = resultSet.getString("id").trim(),
        bekreftetDato = resultSet.getTimestamp("bekreftet_dato")?.toLocalDateTime(),
        behandlingsutfall = objectMapper.readValue(resultSet.getString("behandlings_utfall")),
        legekontorOrgnummer = resultSet.getString("legekontor_org_nr").trim(),
        legeNavn = getLegenavn(resultSet),
        arbeidsgiverNavn = resultSet.getString("arbeidsgivernavn").trim(),
        sykmeldingsperioder = getSyknmeldingsperioder(resultSet).map { modelPeriodeTilSykmeldingsperiode(it) }
    )
}

fun modelPeriodeTilSykmeldingsperiode(periode: Periode): Sykmeldingsperiode {
    return Sykmeldingsperiode(periode.fom, periode.tom, periode.gradert?.grad, finnPeriodetype(periode))
}

fun finnPeriodetype(periode: Periode): Periodetype {
    return when {
        periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
        periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Periodetype.GRADERT
        periode.reisetilskudd -> Periodetype.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }
}

fun getSyknmeldingsperioder(resultSet: ResultSet): List<Periode> {
    return objectMapper.readValue(resultSet.getString("perioder"))
}

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
