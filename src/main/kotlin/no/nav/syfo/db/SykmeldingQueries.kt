package no.nav.syfo.db

import no.nav.syfo.model.BrukerSykmeldingDTO
import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.brukerSykmeldingFromResultSet
import no.nav.syfo.model.persistedSykmeldingFromResultSet
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

fun Database.insertSykmelding(sykmeldingDB: PersistedSykmelding) = connection.use { connection ->
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

const val INSERT_EMPTY_SYKMELDING_METADATA =
    """INSERT INTO sykmelding_metadata(sykmeldingsid, bekreftet_dato) VALUES (?, NULL)"""

fun Database.insertEmptySykmeldingMetadata(sykmeldingsid: String) = connection.use { connection ->
    connection.prepareStatement(INSERT_EMPTY_SYKMELDING_METADATA).use {
        it.setString(1, sykmeldingsid)
        it.executeUpdate()
    }
    connection.commit()
}

const val QUERY_FOR_FNR = """SELECT * FROM sykmelding WHERE pasient_fnr=?;"""

fun Database.find(fnr: String) = connection.use { connection ->
    connection.prepareStatement(QUERY_FOR_FNR).use {
        it.setString(1, fnr)
        it.executeQuery().toList(::persistedSykmeldingFromResultSet)
    }
}

const val QUERY_FOR_BRUKER_SYKMELDING = """
    SELECT id,
       bekreftet_dato,
       behandlings_utfall,
       legekontor_org_nr,
       jsonb_extract_path(sykmelding.sykmelding, 'behandler')::jsonb ->> 'fornavn'    as lege_fornavn,
       jsonb_extract_path(sykmelding.sykmelding, 'behandler')::jsonb ->> 'mellomnavn' as lege_mellomnavn,
       jsonb_extract_path(sykmelding.sykmelding, 'behandler')::jsonb ->> 'etternavn'  as lege_etternavn,
       jsonb_extract_path(sykmelding.sykmelding, 'arbeidsgiver')::jsonb               as arbeidsgiver,
       jsonb_extract_path(sykmelding.sykmelding, 'perioder')::jsonb                   as perioder
    FROM sykmelding LEFT JOIN sykmelding_metadata metadata on sykmelding.id = metadata.sykmeldingsid
    WHERE pasient_fnr=?
    """

fun DatabaseInterface.finnBrukersSykmeldinger(fnr: String): List<BrukerSykmeldingDTO> {
    return connection.use { connection ->
        connection.prepareStatement(QUERY_FOR_BRUKER_SYKMELDING).use {
            it.setString(1, fnr)
            it.executeQuery().toList(::brukerSykmeldingFromResultSet)
        }
    }
}

const val QUERY_FOR_SYKMELDINGS_ID = """SELECT * FROM sykmelding WHERE id=?;"""

fun Database.isSykmeldingStored(sykemldingsId: String) = connection.use { connection ->
    connection.prepareStatement(QUERY_FOR_SYKMELDINGS_ID).use {
        it.setString(1, sykemldingsId)
        it.executeQuery().next()
    }
}

const val UPDATE_METADATA_WITH_BEKREFTET_DATO = """
        UPDATE sykmelding_metadata
        SET bekreftet_dato = current_timestamp
        WHERE sykmeldingsid = ? AND bekreftet_dato IS NULL;
        """

fun DatabaseInterface.registerLestAvBruker(sykmeldingsid: String): Int = connection.use { connection ->
    val status = connection.prepareStatement(UPDATE_METADATA_WITH_BEKREFTET_DATO).use {
        it.setString(1, sykmeldingsid)
        it.executeUpdate()
    }
    connection.commit()
    return status
}

const val QUERY_IS_SYKMELDING_OWNER = """SELECT exists(SELECT 1 FROM sykmelding WHERE id=? AND pasient_fnr=?)"""

fun DatabaseInterface.isSykmeldingOwner(sykmeldingsid: String, fnr: String): Boolean = connection.use { connection ->
    connection.prepareStatement(QUERY_IS_SYKMELDING_OWNER).use {
        it.setString(1, sykmeldingsid)
        it.setString(2, fnr)
        it.executeQuery().next()
    }
}
