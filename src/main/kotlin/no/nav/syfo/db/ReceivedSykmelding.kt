package no.nav.syfo.db

import org.jetbrains.exposed.sql.Table

object ReceivedSykmelding : Table() {
    val id = integer("id").autoIncrement("sykmeldinger").primaryKey()
    val aktoerIdPasient = varchar("aktoerIdPasient", length = 50)
    val aktoerIdLege = varchar("aktoerIdLege", length = 50)
    val navLogId = varchar("navLogId", length = 50)
    val msgId = varchar("msgId", length = 50)
    val legekontorOrgNr = varchar("legekontorOrgNr", length = 50)
    val legekontorOrgName = varchar("legekontorOrgName", length = 50)
    val mottattDato = varchar("mottattDato", length = 50)
}