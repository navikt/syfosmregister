package no.nav.syfo.receivedSykmelding

import no.nav.syfo.db.Database.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

class SykmeldingService {

    suspend fun hentReceivedSykmelding(id: Int): SykmeldingRespons? = dbQuery {
        Sykmelding.select { (Sykmelding.id eq id) }
                .mapNotNull { tilReceivedSykmelding(it) }
                .singleOrNull()
    }

    private fun tilReceivedSykmelding(row: ResultRow): SykmeldingRespons =
            SykmeldingRespons(
                    id = row[Sykmelding.id],
                    aktoerIdPasient = row[Sykmelding.aktoerIdPasient],
                    aktoerIdLege = row[Sykmelding.aktoerIdLege],
                    navLogId = row[Sykmelding.navLogId],
                    msgId = row[Sykmelding.msgId],
                    legekontorOrgNr = row[Sykmelding.legekontorOrgNr],
                    legekontorOrgName = row[Sykmelding.legekontorOrgName],
                    mottattDato = row[Sykmelding.mottattDato].toDateTime()
            )

    suspend fun leggtilSykmelding(nySykmelding: NySykmelding): SykmeldingRespons {
        val id = dbQuery {
            (Sykmelding.insert {
                when (nySykmelding) {
                    is NySykmelding.Sykmelding -> {
                        it[aktoerIdPasient] = nySykmelding.aktoerIdPasient
                        it[aktoerIdLege] = nySykmelding.aktoerIdLege
                        it[navLogId] = nySykmelding.navLogId
                        it[msgId] = nySykmelding.aktoerIdPasient
                        it[legekontorOrgNr] = nySykmelding.aktoerIdPasient
                        it[legekontorOrgName] = nySykmelding.aktoerIdPasient
                        it[mottattDato] = nySykmelding.mottattDato
                    }
                }
            } get Sykmelding.id)!!
        }
        return hentReceivedSykmelding(id)!!
    }
}