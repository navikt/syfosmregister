package no.nav.syfo.aksessering.db

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

fun DatabaseInterface.finnBrukersSykmeldinger(fnr: String): List<Brukersykmelding> {
    val sykmeldinger = connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id,
                    mottak_id,
                    mottatt_tidspunkt,
                    bekreftet_dato,
                    behandlings_utfall,
                    legekontor_org_nr,
                    jsonb_extract_path(sykmelding.sykmelding, 'msgId')::jsonb                      as msg_id,
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

    sykmeldinger.forEach {
        val logValues = arrayOf(
            StructuredArguments.keyValue("mottakId", it.mottakId),
            StructuredArguments.keyValue("msgId", it.msgId),
            StructuredArguments.keyValue("orgNr", it.legekontorOrgnummer),
            StructuredArguments.keyValue("sykmeldingId", it.id)
        )
        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") { "{}" }

        log.info("Henter sykmelding, $logKeys", *logValues)
    }

    return sykmeldinger
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
