package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

data class BrukerSykmelding(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: ValidationResult,
    val legekontorOrgnummer: String,
    val legeNavn: String,
    val arbeidsgiverNavn: String,
    val sykmeldingsperioder: String
)

fun brukerSykmeldingFromResultSet(resultSet: ResultSet): BrukerSykmelding {
    return BrukerSykmelding(
        id = resultSet.getString("id"),
        bekreftetDato = resultSet.getTimestamp("bekreftet_dato")?.toLocalDateTime(),
        behandlingsutfall = objectMapper.readValue(resultSet.getString("behandlings_utfall")),
        legekontorOrgnummer = resultSet.getString("legekontor_org_nr"),
        legeNavn = getLegenavn(resultSet),
        arbeidsgiverNavn = resultSet.getString("arbeidsgivernavn"),
        sykmeldingsperioder = resultSet.getString("perioder")
    )
}

private fun getLegenavn(resultSet: ResultSet) =
    resultSet.getString("lege_fornavn") + resultSet.getString("lege_mellomnavn") + resultSet.getString("lege_etternavn")
