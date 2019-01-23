package no.nav.syfo.receivedSykmelding

import com.fasterxml.jackson.annotation.JsonInclude
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime

object Sykmelding : Table() {
    val id = integer("id").autoIncrement("sykmeldinger").primaryKey()
    val aktoerIdPasient = varchar("aktoerIdPasient", length = 50)
    val aktoerIdLege = varchar("aktoerIdLege", length = 50)
    val navLogId = varchar("navLogId", length = 50)
    val msgId = varchar("msgId", length = 50)
    val legekontorOrgNr = varchar("legekontorOrgNr", length = 50)
    val legekontorOrgName = varchar("legekontorOrgName", length = 50)
    val mottattDato = datetime("mottattDato")
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SykmeldingRespons(
    val id: Int,
    val aktoerIdPasient: String,
    val aktoerIdLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String,
    val legekontorOrgName: String,
    val mottattDato: DateTime
)

sealed class NySykmelding {
    data class Sykmelding(
        var aktoerIdPasient: String,
        val aktoerIdLege: String,
        val navLogId: String,
        val msgId: String,
        val legekontorOrgNr: String,
        val legekontorOrgName: String,
        val mottattDato: DateTime
    ) : NySykmelding()
}