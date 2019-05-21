package no.nav.syfo.aksessering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList

fun DatabaseInterface.finnBrukersSykmeldinger(fnr: String): List<Brukersykmelding> {
    return connection.use { connection ->
        connection.prepareStatement(
            """
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
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList(::brukersykmeldingFromResultSet)
        }
    }
}

fun DatabaseInterface.registrerLestAvBruker(sykmeldingsid: String): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE sykmelding_metadata
            SET bekreftet_dato = current_timestamp
            WHERE sykmeldingsid = ? AND bekreftet_dato IS NULL;
            """
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }

fun DatabaseInterface.erEier(sykmeldingsid: String, fnr: String): Boolean =
    connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT exists(SELECT 1 FROM sykmelding WHERE id=? AND pasient_fnr=?)
            """
        ).use {
            it.setString(1, sykmeldingsid)
            it.setString(2, fnr)
            it.executeQuery().next()
        }
    }
