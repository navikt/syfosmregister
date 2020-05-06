package no.nav.syfo.sykmelding.status

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.OffsetDateTime
import no.nav.syfo.aksessering.db.tilStatusEvent
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.log

fun DatabaseInterface.hentSykmeldingStatuser(sykmeldingId: String): List<SykmeldingStatusEvent> {
    connection.use { connection ->
        return connection.getSykmeldingstatuser(sykmeldingId)
    }
}

fun DatabaseInterface.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        if (!connection.hasNewerStatus(sykmeldingStatusEvent.sykmeldingId, sykmeldingStatusEvent.timestampz)) {
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
        if (!connection.hasNewerStatus(sykmeldingStatusEvent.sykmeldingId, sykmeldingStatusEvent.timestampz)) {
            connection.slettGamleSvarHvisFinnesFraFor(sykmeldingBekreftEvent.sykmeldingId)
            sykmeldingBekreftEvent.sporsmal?.forEach {
                connection.lagreSporsmalOgSvar(it)
            }
        }
        connection.registerStatus(sykmeldingStatusEvent)
        connection.commit()
    }
}

private fun Connection.hasNewerStatus(sykmeldingId: String, timestampz: OffsetDateTime?): Boolean {
    this.prepareStatement("""
        SELECT 1 FROM sykmeldingstatus WHERE sykmelding_id = ? and timestamp > ?
        """).use {
        it.setString(1, sykmeldingId)
        it.setTimestamp(2, Timestamp.from(timestampz!!.toInstant()))
        return it.executeQuery().next()
    }
}

private fun Connection.getSykmeldingstatuser(sykmeldingId: String): List<SykmeldingStatusEvent> {
    this.prepareStatement("""
        SELECT * FROM sykmeldingstatus ss where ss.sykmelding_id = ?
    """).use {
        it.setString(1, sykmeldingId)
        return it.executeQuery().toList { toSykmeldingStatusEvent() }
    }
}

private fun ResultSet.toSykmeldingStatusEvent(): SykmeldingStatusEvent {
    return SykmeldingStatusEvent(
            getString("sykmelding_id"),
            getTimestamp("event_timestamp").toLocalDateTime(),
            tilStatusEvent(getString("event"))
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
                INSERT INTO sykmeldingstatus(sykmelding_id, event_timestamp, event, timestamp) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """
    ).use {
        it.setString(1, sykmeldingStatusEvent.sykmeldingId)
        it.setTimestamp(2, Timestamp.valueOf(sykmeldingStatusEvent.timestamp))
        it.setString(3, sykmeldingStatusEvent.event.name)
        it.setTimestamp(4, getNullableTimestamp(sykmeldingStatusEvent))
        it.execute()
    }
}

private fun getNullableTimestamp(sykmeldingStatusEvent: SykmeldingStatusEvent): Timestamp? {
    return when (sykmeldingStatusEvent.timestampz) {
        null -> {
            log.error("UTC timestamp should not be null")
            null
        }
        else -> Timestamp.from(sykmeldingStatusEvent.timestampz.toInstant())
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
