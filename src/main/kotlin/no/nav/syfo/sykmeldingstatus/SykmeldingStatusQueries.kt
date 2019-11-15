package no.nav.syfo.sykmeldingstatus

import java.lang.RuntimeException
import java.sql.Statement
import no.nav.syfo.db.DatabaseInterface

fun DatabaseInterface.lagreArbeidsgiver(sykmeldingSendEvent: SykmeldingSendEvent) {
    connection.use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO arbeidsgiver(sykmelding_id, orgnummer, juridisk_orgnummer, navn) VALUES (?, ?, ?, ?)
                    """
        ).use {
            it.setString(1, sykmeldingSendEvent.id)
            it.setString(2, sykmeldingSendEvent.arbeidsgiver.orgnummer)
            it.setString(3, sykmeldingSendEvent.arbeidsgiver.juridiskOrgnummer)
            it.setString(4, sykmeldingSendEvent.arbeidsgiver.orgnavn)
            it.execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.lagreSporsmalOgSvar(sporsmal: Sporsmal) {
    var spmId: Int? = finnSporsmal(sporsmal)
    if (spmId == null) {
        spmId = lagreSporsmal(sporsmal)
    }
    lagreSvar(spmId, sporsmal.svar)
}

fun DatabaseInterface.lagreSporsmal(sporsmal: Sporsmal): Int {
    var spmId: Int? = null
    connection.use { connection ->
        connection.prepareStatement(
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
        connection.commit()
    }
    return spmId ?: throw RuntimeException("Fant ikke id for spørsmål som nettopp ble lagret")
}

fun DatabaseInterface.finnSporsmal(sporsmal: Sporsmal): Int? {
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT sporsmal.id
                FROM sporsmal
                WHERE shortName=? AND tekst=?;
                """
        ).use {
            it.setString(1, sporsmal.shortName.name)
            it.setString(2, sporsmal.tekst)
            return it.executeQuery().getInt(1)
        }
    }
}

private fun DatabaseInterface.lagreSvar(sporsmalId: Int, svar: Svar) {
    connection.use { connection ->
        connection.prepareStatement(
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
        connection.commit()
    }
}
