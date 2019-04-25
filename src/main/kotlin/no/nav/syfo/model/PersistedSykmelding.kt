package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.sql.ResultSet
import java.time.LocalDateTime

data class PersistedSykmelding(
    val id: String,
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
    val sykmelding: Sykmelding,
    val behandlingsUtfall: ValidationResult
)

fun fromResultSet(resultSet: ResultSet) = PersistedSykmelding(
        resultSet.getString("id").trim(),
        resultSet.getString("pasient_fnr").trim(),
        resultSet.getString("pasient_aktoer_id").trim(),
        resultSet.getString("lege_fnr").trim(),
        resultSet.getString("lege_aktoer_id").trim(),
        resultSet.getString("mottak_id").trim(),
        resultSet.getString("legekontor_org_nr").trim(),
        resultSet.getString("legekontor_her_id").trim(),
        resultSet.getString("legekontor_resh_id").trim(),
        resultSet.getString("epj_system_navn").trim(),
        resultSet.getString("epj_system_versjon").trim(),
        resultSet.getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        objectMapper.readValue(resultSet.getString("sykmelding")),
        objectMapper.readValue(resultSet.getString("behandlings_utfall"))
)

fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

fun ValidationResult.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
