package no.nav.syfo.db

import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.fromResultSet
import no.nav.syfo.model.toPGObject
import java.sql.Timestamp

const val INSERT_QUERY = """
INSERT INTO sykmelding(
    pasient_fnr),
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
    sykmelding
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""

const val QUERY_FOR_FNR = """SELECT * FROM sykmelding WHERE pasient_fnr=?;"""

fun Database.insertSykmelding(sykmeldingDB: PersistedSykmelding) = connection.prepareStatement(INSERT_QUERY).use {
    it.setString(1, sykmeldingDB.pasientFnr)
    it.setString(2, sykmeldingDB.pasientAktoerId)
    it.setString(3, sykmeldingDB.legeFnr)
    it.setString(4, sykmeldingDB.legeAktoerId)
    it.setString(5, sykmeldingDB.mottakId)
    it.setString(6, sykmeldingDB.legekontorOrgNr)
    it.setString(7, sykmeldingDB.legekontorHerId)
    it.setString(8, sykmeldingDB.legekontorReshId)
    it.setString(9, sykmeldingDB.epjSystemNavn)
    it.setString(10, sykmeldingDB.epjSystemVersjon)
    it.setTimestamp(11, Timestamp.valueOf(sykmeldingDB.mottattTidspunkt))
    it.setObject(12, sykmeldingDB.sykmelding.toPGObject())
    it.execute()
}

fun Database.find(pasientFNR: String) = connection.prepareStatement(QUERY_FOR_FNR).use {
    it.setString(1, pasientFNR)
    it.executeQuery().toList(::fromResultSet)
}
