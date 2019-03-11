package no.nav.syfo.db

import org.jetbrains.exposed.sql.Table

object Sykmelding : Table() {
    val id = integer("id").autoIncrement("sykmeldinger").primaryKey()
    val aktoerIdPasient = varchar("aktoeridpasient", length = 50)
    val aktoerIdLege = varchar("aktoeridlege", length = 50)
    val navLogId = varchar("navlogid", length = 50)
    val msgId = varchar("msgid", length = 50)
    val legekontorOrgNr = varchar("legekontororgnr", length = 50).nullable()
    val legekontorHerId = varchar("legekontorherid", length = 50).nullable()
    val legekontorReshId = varchar("legekontorreshid", length = 50).nullable()
    val legekontorOrgName = varchar("legekontororgname", length = 100)
    val mottattDato = datetime("mottattdato")
}