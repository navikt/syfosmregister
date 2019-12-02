package no.nav.syfo.sykmeldingstatus

import java.lang.RuntimeException
import java.sql.Connection
import java.sql.Statement
import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log

fun DatabaseInterface.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        connection.registerStatus(sykmeldingStatusEvent)
        connection.commit()
    }
}

fun DatabaseInterface.registrerSendt(sykmeldingSendEvent: SykmeldingSendEvent, sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        connection.slettGamleSvarHvisFinnesFraFor(sykmeldingSendEvent.sykmeldingId)
        connection.registerStatus(sykmeldingStatusEvent)
        connection.lagreArbeidsgiver(sykmeldingSendEvent)
        connection.lagreSporsmalOgSvar(sykmeldingSendEvent.sporsmal)
        connection.commit()
    }
}

fun DatabaseInterface.registrerBekreftet(sykmeldingBekreftEvent: SykmeldingBekreftEvent, sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        connection.slettGamleSvarHvisFinnesFraFor(sykmeldingBekreftEvent.sykmeldingId)
        connection.registerStatus(sykmeldingStatusEvent)
        sykmeldingBekreftEvent.sporsmal?.forEach {
            connection.lagreSporsmalOgSvar(it)
        }
        connection.commit()
    }
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

private fun Connection.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    this.prepareStatement(
        """
                INSERT INTO sykmeldingstatus(sykmelding_id, event_timestamp, event) VALUES (?, ?, ?)
                """
    ).use {
        it.setString(1, sykmeldingStatusEvent.sykmeldingId)
        it.setTimestamp(2, Timestamp.valueOf(sykmeldingStatusEvent.timestamp))
        it.setString(3, sykmeldingStatusEvent.event.name)
        it.execute()
    }
}

private fun Connection.lagreArbeidsgiver(sykmeldingSendEvent: SykmeldingSendEvent) {
    this.prepareStatement(
        """
                INSERT INTO arbeidsgiver(sykmelding_id, orgnummer, juridisk_orgnummer, navn) VALUES (?, ?, ?, ?)
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
