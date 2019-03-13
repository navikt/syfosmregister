package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.time.LocalDateTime

data class PersistedSykmelding(
    val pasientFnr: String,
    val pasientAktoerId: String,
    val legeFnr: String,
    val legeAktoerId: String,
    val mottakId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val epjSystemNavn: String,
    val epjSystemVersjon: String,
    val mottattTidspunkt: LocalDateTime,
    val sykmelding_json: Sykmelding
)

fun fromResultSet(resultSet: ResultSet) = PersistedSykmelding(
        resultSet.getString("pasient_fnr"),
        resultSet.getString("pasient_aktoer_id"),
        resultSet.getString("lege_fnr"),
        resultSet.getString("lege_aktoer_id"),
        resultSet.getString("mottak_id"),
        resultSet.getString("legekontor_org_nr"),
        resultSet.getString("legekontor_her_id"),
        resultSet.getString("legekontor_resh_id"),
        resultSet.getString("epj_system_navn"),
        resultSet.getString("epj_system_versjon"),
        resultSet.getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        objectMapper.readValue(resultSet.getString("sykmelding_json"))
)

fun Sykmelding.toPGObject() = PGobject().apply {
    type = "json"
    value = objectMapper.writeValueAsString(this)
}
