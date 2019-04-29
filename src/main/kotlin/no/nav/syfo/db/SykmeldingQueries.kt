package no.nav.syfo.db

import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.fromResultSet
import no.nav.syfo.model.toPGObject
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

fun Database.insertSykmelding(sykmeldingDB: PersistedSykmelding) = connection.use {
    val ps = it.prepareStatement(INSERT_QUERY)
    ps.setString(1, sykmeldingDB.id)
    ps.setString(2, sykmeldingDB.pasientFnr)
    ps.setString(3, sykmeldingDB.pasientAktoerId)
    ps.setString(4, sykmeldingDB.legeFnr)
    ps.setString(5, sykmeldingDB.legeAktoerId)
    ps.setString(6, sykmeldingDB.mottakId)
    ps.setString(7, sykmeldingDB.legekontorOrgNr)
    ps.setString(8, sykmeldingDB.legekontorHerId)
    ps.setString(9, sykmeldingDB.legekontorReshId)
    ps.setString(10, sykmeldingDB.epjSystemNavn)
    ps.setString(11, sykmeldingDB.epjSystemVersjon)
    ps.setTimestamp(12, Timestamp.valueOf(sykmeldingDB.mottattTidspunkt))
    ps.setObject(13, sykmeldingDB.sykmelding.toPGObject())
    ps.setObject(14, sykmeldingDB.behandlingsUtfall.toPGObject())
    ps.executeUpdate()
    it.commit()
}

const val QUERY_FOR_FNR = """SELECT * FROM sykmelding WHERE pasient_fnr=?;"""

fun Database.find(fnr: String) = connection.use { connection ->
    connection.prepareStatement(QUERY_FOR_FNR).use {
        it.setString(1, fnr)
        it.executeQuery().toList(::fromResultSet)
    }
}

const val QUERY_FOR_SYKMELDINGS_ID = """SELECT * FROM sykmelding WHERE id=?;"""

fun Database.isSykmeldingStored(sykemldingsId: String) = connection.use { connection ->
    connection.prepareStatement(QUERY_FOR_SYKMELDINGS_ID).use {
        it.setString(1, sykemldingsId)
        it.executeQuery().next()
    }
}

const val INSERT_LEST_AV_BRUKER_QUERY = """
        INSERT INTO sykmelding_metadata (sykmeldingsid, lest_av_bruker)
        VALUES (?, localtimestamp);
        """

fun Database.registerLestAvBruker(sykmeldingsid: String): Boolean = connection.use { connection ->
    connection.prepareStatement(INSERT_LEST_AV_BRUKER_QUERY).use {
        it.setString(1, sykmeldingsid)
        it.executeQuery().next()
    }
}

const val QUERY_IS_SYKMELDING_OWNER = """SELECT exists(SELECT 1 FROM sykmelding WHERE id=? AND pasient_fnr=?)"""

fun Database.isSykmeldingOwner(sykmeldingsid: String, fnr: String): Boolean = connection.use { connection ->
    connection.prepareStatement(QUERY_IS_SYKMELDING_OWNER).use {
        it.setString(1, sykmeldingsid)
        it.setString(2, fnr)
        it.executeQuery().next()
    }
}
