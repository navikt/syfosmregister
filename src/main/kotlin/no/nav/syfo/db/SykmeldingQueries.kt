package no.nav.syfo.db

import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.fromResultSet
import no.nav.syfo.model.toPGObject
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

fun Database.insertSykmelding(sykmeldingDB: PersistedSykmelding) = insertSykmelding(sykmeldingDB, connection)
fun insertSykmelding(sykmelding: PersistedSykmelding, connection: Connection) {
    connection.use {
        val ps = it.prepareStatement(INSERT_QUERY)
        ps.setString(1, sykmelding.id)
        ps.setString(2, sykmelding.pasientFnr)
        ps.setString(3, sykmelding.pasientAktoerId)
        ps.setString(4, sykmelding.legeFnr)
        ps.setString(5, sykmelding.legeAktoerId)
        ps.setString(6, sykmelding.mottakId)
        ps.setString(7, sykmelding.legekontorOrgNr)
        ps.setString(8, sykmelding.legekontorHerId)
        ps.setString(9, sykmelding.legekontorReshId)
        ps.setString(10, sykmelding.epjSystemNavn)
        ps.setString(11, sykmelding.epjSystemVersjon)
        ps.setTimestamp(12, Timestamp.valueOf(sykmelding.mottattTidspunkt))
        ps.setObject(13, sykmelding.sykmelding.toPGObject())
        ps.setObject(14, sykmelding.behandlingsUtfall.toPGObject())
        ps.executeUpdate()
        it.commit()
    }
}

const val QUERY_FOR_SYKMELDING_GIVEN_FNR = """SELECT * FROM sykmelding WHERE pasient_fnr=?;"""
fun Database.findWithFnr(fnr: String): MutableList<PersistedSykmelding> = findWithFnr(fnr, connection)
fun findWithFnr(fnr: String, connection: Connection): MutableList<PersistedSykmelding> {
    return connection.prepareStatement(QUERY_FOR_SYKMELDING_GIVEN_FNR).use {
        it.setString(1, fnr)
        it.executeQuery().toList(::fromResultSet)
    }
}

const val QUERY_FOR_SYKMELDING_GIVEN_SYKMELDING_ID = """SELECT * FROM sykmelding WHERE id=?;"""
fun Database.isSykmeldingStored(sykemeldingId: String): Boolean = isSykemeldingStored(sykemeldingId, connection)
fun isSykemeldingStored(sykemeldingId: String, connection: Connection): Boolean {
    return connection.prepareStatement(QUERY_FOR_SYKMELDING_GIVEN_SYKMELDING_ID).use {
        it.setString(1, sykemeldingId)
        it.executeQuery().next()
    }
}

const val QUERY_FOR_SYKMELDING_FOR_PERSON_GIVEN_SYKMELDING_ID =
    """SELECT * FROM sykmelding WHERE id=? AND pasient_fnr=?;"""

fun Database.findWithId(sykmeldingId: String, ownerFnr: String): PersistedSykmelding =
    findWithId(sykmeldingId, ownerFnr, connection)

fun findWithId(sykmeldingId: String, ownerFnr: String, connection: Connection): PersistedSykmelding {
    return connection.prepareStatement(QUERY_FOR_SYKMELDING_FOR_PERSON_GIVEN_SYKMELDING_ID).use {
        it.setString(1, sykmeldingId)
        it.setString(2, ownerFnr)
        it.executeQuery().toList(::fromResultSet).first()
    }
}
