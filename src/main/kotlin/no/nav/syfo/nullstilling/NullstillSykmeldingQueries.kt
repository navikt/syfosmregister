package no.nav.syfo.nullstilling

import no.nav.syfo.persistering.PersistedSykmelding
import no.nav.syfo.persistering.toPGObject
import java.sql.Connection
import java.sql.Timestamp

const val INSERT_QUERY = """
INSERT INTO sykmelding(
    id,
    pasient_fnr,
    pasient_aktoer_id,
    lege_fnr,
    lege_aktoer_id,
    mottak_id,
    legekontor_org_nr,
    legekontor_her_id,
    legekontor_resh_id,
    epj_system_navn,
    epj_system_versjon,
    mottatt_tidspunkt,
    sykmelding,
    behandlings_utfall
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""

fun Connection.opprettSykmelding(sykmeldingDB: PersistedSykmelding) {
    use { connection ->
        connection.prepareStatement(INSERT_QUERY).use {
            it.setString(1, sykmeldingDB.id)
            it.setString(2, sykmeldingDB.pasientFnr)
            it.setString(3, sykmeldingDB.pasientAktoerId)
            it.setString(4, sykmeldingDB.legeFnr)
            it.setString(5, sykmeldingDB.legeAktoerId)
            it.setString(6, sykmeldingDB.mottakId)
            it.setString(7, sykmeldingDB.legekontorOrgNr)
            it.setString(8, sykmeldingDB.legekontorHerId)
            it.setString(9, sykmeldingDB.legekontorReshId)
            it.setString(10, sykmeldingDB.epjSystemNavn)
            it.setString(11, sykmeldingDB.epjSystemVersjon)
            it.setTimestamp(12, Timestamp.valueOf(sykmeldingDB.mottattTidspunkt))
            it.setObject(13, sykmeldingDB.sykmelding.toPGObject())
            it.setObject(14, sykmeldingDB.behandlingsUtfall.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}
