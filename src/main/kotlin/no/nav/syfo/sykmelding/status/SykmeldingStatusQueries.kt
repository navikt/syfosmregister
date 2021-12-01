package no.nav.syfo.sykmelding.status

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.log
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun DatabaseInterface.hentSykmeldingStatuser(sykmeldingId: String): List<SykmeldingStatusEvent> {
    connection.use { connection ->
        return connection.getSykmeldingstatuser(sykmeldingId)
    }
}

fun DatabaseInterface.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        if (!connection.hasNewerStatus(sykmeldingStatusEvent.sykmeldingId, sykmeldingStatusEvent.timestamp)) {
            connection.slettAlleSvar(sykmeldingStatusEvent.sykmeldingId)
        }
        connection.registerStatus(sykmeldingStatusEvent)
        connection.commit()
    }
}

fun DatabaseInterface.registrerSendt(sykmeldingSendEvent: SykmeldingSendEvent, sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        connection.slettGamleSvarHvisFinnesFraFor(sykmeldingSendEvent.sykmeldingId)
        connection.lagreArbeidsgiverStatus(sykmeldingSendEvent)
        connection.lagreSporsmalOgSvar(sykmeldingSendEvent.sporsmal)
        connection.registerStatus(sykmeldingStatusEvent)
        connection.commit()
    }
}

fun DatabaseInterface.registrerBekreftet(sykmeldingBekreftEvent: SykmeldingBekreftEvent, sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        if (!connection.hasNewerStatus(sykmeldingStatusEvent.sykmeldingId, sykmeldingStatusEvent.timestamp)) {
            connection.slettGamleSvarHvisFinnesFraFor(sykmeldingBekreftEvent.sykmeldingId)
            sykmeldingBekreftEvent.sporsmal?.forEach {
                connection.lagreSporsmalOgSvar(it)
            }
        }
        connection.registerStatus(sykmeldingStatusEvent)
        connection.commit()
    }
}

fun DatabaseInterface.erEier(sykmeldingsid: String, fnr: String): Boolean =
    connection.use { connection ->
        connection.prepareStatement(
            """
           SELECT 1 FROM SYKMELDINGSOPPLYSNINGER WHERE id=? AND pasient_fnr=?
            """
        ).use {
            it.setString(1, sykmeldingsid)
            it.setString(2, fnr)
            it.executeQuery().next()
        }
    }

private fun Connection.hasNewerStatus(sykmeldingId: String, timestamp: OffsetDateTime): Boolean {
    this.prepareStatement(
        """
        SELECT 1 FROM sykmeldingstatus WHERE sykmelding_id = ? and timestamp > ?
        """
    ).use {
        it.setString(1, sykmeldingId)
        it.setTimestamp(2, Timestamp.from(timestamp.toInstant()))
        return it.executeQuery().next()
    }
}

private fun Connection.getSykmeldingstatuser(sykmeldingId: String): List<SykmeldingStatusEvent> {
    this.prepareStatement(
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
    """
    ).use {
        it.setString(1, sykmeldingId)
        return it.executeQuery().toList { toSykmeldingStatusEvent() }
    }
}

private fun ResultSet.toSykmeldingStatusEvent(): SykmeldingStatusEvent {
    return SykmeldingStatusEvent(
        sykmeldingId = getString("sykmelding_id"),
        timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
        event = tilStatusEvent(getString("event")),
        erAvvist = objectMapper.readValue(getString("behandlingsutfall"), ValidationResult::class.java).status == Status.INVALID,
        erEgenmeldt = getString("epj_system_navn") == "Egenmeldt"
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
    this.prepareStatement(
        """
                SELECT 1 FROM svar WHERE sykmelding_id=?;
                """
    ).use {
        it.setString(1, sykmeldingId)
        it.executeQuery().next()
    }

fun Connection.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    this.prepareStatement(
        """
                INSERT INTO sykmeldingstatus(sykmelding_id, event, timestamp) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setString(1, sykmeldingStatusEvent.sykmeldingId)
        it.setString(2, sykmeldingStatusEvent.event.name)
        it.setTimestamp(3, Timestamp.from(sykmeldingStatusEvent.timestamp.toInstant()))
        it.execute()
    }
}

private fun Connection.lagreArbeidsgiverStatus(sykmeldingSendEvent: SykmeldingSendEvent) {
    this.prepareStatement(
        """
                INSERT INTO arbeidsgiver(sykmelding_id, orgnummer, juridisk_orgnummer, navn) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setString(1, sykmeldingSendEvent.sykmeldingId)
        it.setString(2, sykmeldingSendEvent.arbeidsgiver.orgnummer)
        it.setString(3, sykmeldingSendEvent.arbeidsgiver.juridiskOrgnummer)
        it.setString(4, sykmeldingSendEvent.arbeidsgiver.orgnavn)
        it.execute()
    }
}

private fun Connection.slettAlleSvar(sykmeldingId: String) {
    this.slettArbeidsgiver(sykmeldingId)
    this.slettSvar(sykmeldingId)
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
    this.prepareStatement(
        """
                INSERT INTO sporsmal(shortName, tekst) VALUES (?, ?)
                """,
        Statement.RETURN_GENERATED_KEYS
    ).use {
        it.setString(1, sporsmal.shortName.name)
        it.setString(2, sporsmal.tekst)
        it.execute()
        if (it.generatedKeys.next()) {
            spmId = it.generatedKeys.getInt(1)
        }
    }
    return spmId ?: throw RuntimeException("Fant ikke id for spørsmål som nettopp ble lagret")
}

private fun Connection.finnSporsmal(sporsmal: Sporsmal): Int? {
    this.prepareStatement(
        """
                SELECT sporsmal.id
                FROM sporsmal
                WHERE shortName=? AND tekst=?;
                """
    ).use {
        it.setString(1, sporsmal.shortName.name)
        it.setString(2, sporsmal.tekst)
        val rs = it.executeQuery()
        return if (rs.next()) rs.getInt(1) else null
    }
}

private fun Connection.lagreSvar(sporsmalId: Int, svar: Svar) {
    this.prepareStatement(
        """
                INSERT INTO svar(sykmelding_id, sporsmal_id, svartype, svar) VALUES (?, ?, ?, ?)
                """
    ).use {
        it.setString(1, svar.sykmeldingId)
        it.setInt(2, sporsmalId)
        it.setString(3, svar.svartype.name)
        it.setString(4, svar.svar)
        it.execute()
    }
}

private fun Connection.slettArbeidsgiver(sykmeldingId: String) {
    this.prepareStatement(
        """
                DELETE FROM arbeidsgiver WHERE sykmelding_id=?;
                """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun Connection.slettSvar(sykmeldingId: String) {
    this.prepareStatement(
        """
                DELETE FROM svar WHERE sykmelding_id=?;
                """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun tilStatusEvent(status: String): StatusEvent {
    return when (status) {
        "BEKREFTET" -> StatusEvent.BEKREFTET
        "APEN" -> StatusEvent.APEN
        "SENDT" -> StatusEvent.SENDT
        "AVBRUTT" -> StatusEvent.AVBRUTT
        "UTGATT" -> StatusEvent.UTGATT
        else -> throw IllegalStateException("Sykmeldingen har ukjent status eller er slettet, skal ikke kunne skje")
    }
}
