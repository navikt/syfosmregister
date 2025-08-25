package no.nav.syfo.sykmelding.status

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.kafka.model.KomplettInnsendtSkjemaSvar
import no.nav.syfo.sykmelding.kafka.model.TidligereArbeidsgiverKafkaDTO
import org.postgresql.util.PGobject

suspend fun DatabaseInterface.hentSykmeldingStatuser(
    sykmeldingId: String
): List<SykmeldingStatusEvent> =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getSykmeldingstatuser(sykmeldingId) }
    }

suspend fun DatabaseInterface.getTidligereArbeidsgiver(sykmeldingId: String) =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getTidligereArbeidsgiver(sykmeldingId) }
    }

suspend fun DatabaseInterface.getAlleSpm(sykmeldingId: String) =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getAlleSpm(sykmeldingId) }
    }

suspend fun DatabaseInterface.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) =
    withContext(Dispatchers.IO) {
        if (sykmeldingStatusEvent.event == StatusEvent.SLETTET) {
            throw IllegalArgumentException(
                "Cannot insert slettet status in db sykmeldingId ${sykmeldingStatusEvent.sykmeldingId}"
            )
        }
        connection.use { connection ->
            if (
                !connection.hasNewerStatus(
                    sykmeldingStatusEvent.sykmeldingId,
                    sykmeldingStatusEvent.timestamp
                )
            ) {
                connection.slettAlleSvar(sykmeldingStatusEvent.sykmeldingId)
            }
            connection.registerStatus(sykmeldingStatusEvent)
            connection.commit()
        }
    }

suspend fun DatabaseInterface.registrerSendt(
    sykmeldingSendEvent: SykmeldingSendEvent,
    sykmeldingStatusEvent: SykmeldingStatusEvent
) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection.slettGamleSvarHvisFinnesFraFor(sykmeldingSendEvent.sykmeldingId)
            connection.lagreArbeidsgiverStatus(sykmeldingSendEvent)
            sykmeldingSendEvent.sporsmal.forEach { connection.lagreSporsmalOgSvar(it) }
            sykmeldingSendEvent.brukerSvar?.let {
                connection.insertAlleSpm(it, sykmeldingSendEvent.sykmeldingId)
            }
            connection.registerStatus(sykmeldingStatusEvent)
            connection.commit()
        }
    }

private fun Connection.slettTidligereArbeidsgiver(sykmeldingId: String) =
    prepareStatement(
            """
            DELETE FROM tidligere_arbeidsgiver WHERE sykmelding_id=?;
            """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }

suspend fun DatabaseInterface.registrerBekreftet(
    sykmeldingBekreftEvent: SykmeldingBekreftEvent,
    sykmeldingStatusEvent: SykmeldingStatusEvent,
    tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO?,
) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            if (
                !connection.hasNewerStatus(
                    sykmeldingStatusEvent.sykmeldingId,
                    sykmeldingStatusEvent.timestamp
                )
            ) {
                connection.slettGamleSvarHvisFinnesFraFor(sykmeldingBekreftEvent.sykmeldingId)
                sykmeldingBekreftEvent.sporsmal?.forEach { connection.lagreSporsmalOgSvar(it) }
                if (tidligereArbeidsgiver != null) {
                    connection.registerTidligereArbeidsgiver(
                        sykmeldingStatusEvent.sykmeldingId,
                        tidligereArbeidsgiver
                    )
                }
                sykmeldingBekreftEvent.brukerSvar?.let {
                    connection.insertAlleSpm(it, sykmeldingBekreftEvent.sykmeldingId)
                }
            }
            connection.registerStatus(sykmeldingStatusEvent)

            connection.commit()
        }
    }

private fun Connection.insertAlleSpm(alleSpm: KomplettInnsendtSkjemaSvar, sykmeldingId: String) {
    prepareStatement(
            """
        insert into status_all_spm(sykmelding_id, alle_spm) 
        values (?, ?) 
        on conflict(sykmelding_id) do update 
        set alle_spm = excluded.alle_spm
    """
        )
        .use { ps ->
            ps.setString(1, sykmeldingId)
            ps.setObject(
                2,
                alleSpm.let {
                    PGobject().apply {
                        type = "json"
                        value = objectMapper.writeValueAsString(it)
                    }
                }
            )
            ps.executeUpdate()
        }
}

private fun Connection.hasNewerStatus(sykmeldingId: String, timestamp: OffsetDateTime): Boolean =
    prepareStatement(
            """
        SELECT 1 FROM sykmeldingstatus WHERE sykmelding_id = ? and timestamp > ?
        """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.setTimestamp(2, Timestamp.from(timestamp.toInstant()))
            it.executeQuery().next()
        }

private fun Connection.getTidligereArbeidsgiver(sykmeldingId: String): List<TidligereArbeidsgiver> =
    prepareStatement(
            """
                    SELECT *
                    FROM tidligere_arbeidsgiver 
                    WHERE sykmelding_id = ?
                    """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilTidligereArbeidsgiverliste() }
        }

private fun Connection.getAlleSpm(sykmeldingId: String) =
    prepareStatement(
            """
        select alle_spm from status_all_spm where sykmelding_id = ?;
    """
        )
        .use { ps ->
            ps.setString(1, sykmeldingId)
            ps.executeQuery().let {
                when (it.next()) {
                    true ->
                        objectMapper.readValue<KomplettInnsendtSkjemaSvar>(it.getString("alle_spm"))
                    false -> null
                }
            }
        }

data class TidligereArbeidsgiver(
    val sykmeldingId: String,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO
)

private fun ResultSet.tilTidligereArbeidsgiverliste(): TidligereArbeidsgiver =
    TidligereArbeidsgiver(
        sykmeldingId = getString("sykmelding_id"),
        tidligereArbeidsgiver =
            getString("tidligere_arbeidsgiver").let {
                objectMapper.readValue<TidligereArbeidsgiverDTO>(it)
            }
    )

private fun Connection.getSykmeldingstatuser(sykmeldingId: String): List<SykmeldingStatusEvent> =
    prepareStatement(
            """
        SELECT status.sykmelding_id,
        status.timestamp,
        status.event,
        utfall.behandlingsutfall,
        opplysninger.epj_system_navn 
        FROM sykmeldingstatus AS status 
            INNER JOIN sykmeldingsopplysninger AS opplysninger ON status.sykmelding_id = opplysninger.id
            INNER JOIN behandlingsutfall AS utfall ON status.sykmelding_id = utfall.id 
        WHERE status.sykmelding_id = ?
    """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toSykmeldingStatusEvent() }
        }

private fun ResultSet.toSykmeldingStatusEvent(): SykmeldingStatusEvent {
    return SykmeldingStatusEvent(
        sykmeldingId = getString("sykmelding_id"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
        event = tilStatusEvent(getString("event")),
        erAvvist =
            objectMapper
                .readValue(getString("behandlingsutfall"), ValidationResult::class.java)
                .status == Status.INVALID,
        erEgenmeldt = getString("epj_system_navn") == "Egenmeldt",
    )
}

private fun Connection.slettGamleSvarHvisFinnesFraFor(sykmeldingId: String) {
    val svarFinnesFraFor = this.svarFinnesFraFor(sykmeldingId)
    if (svarFinnesFraFor) {
        log.info("Sletter tidligere svar for sykmelding {}", sykmeldingId)
        this.slettAlleSvar(sykmeldingId)
    }
}

private fun Connection.svarFinnesFraFor(sykmeldingId: String): Boolean =
    prepareStatement(
            """
                SELECT 1 FROM svar WHERE sykmelding_id=?;
                """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeQuery().next()
        }

fun Connection.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) =
    prepareStatement(
            """
                INSERT INTO sykmeldingstatus(sykmelding_id, event, timestamp) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """,
        )
        .use {
            it.setString(1, sykmeldingStatusEvent.sykmeldingId)
            it.setString(2, sykmeldingStatusEvent.event.name)
            it.setTimestamp(3, Timestamp.from(sykmeldingStatusEvent.timestamp.toInstant()))
            it.execute()
        }

fun Connection.registerTidligereArbeidsgiver(
    sykmeldingId: String,
    tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO
) =
    prepareStatement(
            """
                INSERT INTO tidligere_arbeidsgiver(sykmelding_id, tidligere_arbeidsgiver) VALUES (?, ?) ON CONFLICT DO NOTHING
                """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.setObject(2, tidligereArbeidsgiver.toPGObject())
            it.execute()
        }

private fun Connection.lagreArbeidsgiverStatus(sykmeldingSendEvent: SykmeldingSendEvent) =
    prepareStatement(
            """
                INSERT INTO arbeidsgiver(sykmelding_id, orgnummer, juridisk_orgnummer, navn) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """,
        )
        .use {
            it.setString(1, sykmeldingSendEvent.sykmeldingId)
            it.setString(2, sykmeldingSendEvent.arbeidsgiver.orgnummer)
            it.setString(3, sykmeldingSendEvent.arbeidsgiver.juridiskOrgnummer)
            it.setString(4, sykmeldingSendEvent.arbeidsgiver.orgnavn)
            it.execute()
        }

private fun Connection.slettAlleSvar(sykmeldingId: String) {
    this.slettArbeidsgiver(sykmeldingId)
    val antall = slettTidligereArbeidsgiver(sykmeldingId)
    if (antall > 0) {
        log.info("sletta antall tidligere arbeidsgivere: $antall")
    }
    val slettetBrukerSvar = slettBrukerSvar(sykmeldingId)
    if (slettetBrukerSvar > 0) {
        log.info("Slette gamle stauts_alle_spm for $sykmeldingId")
    }
    this.slettSvar(sykmeldingId)
}

fun Connection.slettBrukerSvar(sykmeldingId: String) =
    prepareStatement(
            """
                DELETE FROM status_all_spm WHERE sykmelding_id=?;
                """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeUpdate()
        }

private fun Connection.lagreSporsmalOgSvar(sporsmal: Sporsmal) {
    var spmId: Int?
    spmId = this.finnSporsmal(sporsmal)
    if (spmId == null) {
        spmId = this.lagreSporsmal(sporsmal)
    }
    this.lagreSvar(spmId, sporsmal.svar)
}

private fun Connection.lagreSporsmal(sporsmal: Sporsmal): Int {
    var spmId: Int? = null
    prepareStatement(
            """
                INSERT INTO sporsmal(shortName, tekst) VALUES (?, ?)
                """,
            Statement.RETURN_GENERATED_KEYS,
        )
        .use {
            it.setString(1, sporsmal.shortName.name)
            it.setString(2, sporsmal.tekst)
            it.execute()
            if (it.generatedKeys.next()) {
                spmId = it.generatedKeys.getInt(1)
            }
        }
    return spmId ?: throw RuntimeException("Fant ikke id for spørsmål som nettopp ble lagret")
}

private fun Connection.finnSporsmal(sporsmal: Sporsmal): Int? =
    prepareStatement(
            """
                SELECT sporsmal.id
                FROM sporsmal
                WHERE shortName=? AND tekst=?;
                """,
        )
        .use {
            it.setString(1, sporsmal.shortName.name)
            it.setString(2, sporsmal.tekst)
            val rs = it.executeQuery()
            if (rs.next()) rs.getInt(1) else null
        }

private fun Connection.lagreSvar(sporsmalId: Int, svar: Svar) =
    prepareStatement(
            """
                INSERT INTO svar(sykmelding_id, sporsmal_id, svartype, svar) VALUES (?, ?, ?, ?)
                """,
        )
        .use {
            it.setString(1, svar.sykmeldingId)
            it.setInt(2, sporsmalId)
            it.setString(3, svar.svartype.name)
            it.setString(4, svar.svar)
            it.execute()
        }

private fun Connection.slettArbeidsgiver(sykmeldingId: String) =
    prepareStatement(
            """
                DELETE FROM arbeidsgiver WHERE sykmelding_id=?;
                """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.execute()
        }

private fun Connection.slettSvar(sykmeldingId: String) =
    prepareStatement(
            """
                DELETE FROM svar WHERE sykmelding_id=?;
                """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.execute()
        }

private fun tilStatusEvent(status: String): StatusEvent {
    return when (status) {
        "BEKREFTET" -> StatusEvent.BEKREFTET
        "APEN" -> StatusEvent.APEN
        "SENDT" -> StatusEvent.SENDT
        "AVBRUTT" -> StatusEvent.AVBRUTT
        "UTGATT" -> StatusEvent.UTGATT
        else ->
            throw IllegalStateException(
                "Sykmeldingen har ukjent status eller er slettet, skal ikke kunne skje"
            )
    }
}

private fun TidligereArbeidsgiverKafkaDTO.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }
