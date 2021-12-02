package no.nav.syfo.nullstilling

import java.sql.Connection
import no.nav.syfo.db.DatabaseInterface
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun DatabaseInterface.nullstillSykmeldinger(aktorId: String) {
    val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")
    connection.use { connection ->
        log.info("Nullstiller sykmeldinger på aktor: {}", aktorId)
        connection.prepareStatement(
                """
                DELETE FROM behandlingsutfall
                WHERE id IN (
                    SELECT id
                    FROM sykmeldingsopplysninger
                    WHERE pasient_aktoer_id=?
                );

                DELETE FROM SYKMELDINGSOPPLYSNINGER
                WHERE pasient_aktoer_id=?;
            """
        ).use {
            it.setString(1, aktorId)
            it.setString(2, aktorId)
            it.execute()
        }
        connection.commit()

        log.info("Utført: slettet sykmeldinger på aktør: {}", aktorId)
    }
}

fun DatabaseInterface.slettSykmelding(sykmeldingId: String) {
    connection.use { connection ->
        deleteBehandlingsutfall(connection, sykmeldingId)
        deleteArbeidsgiver(connection, sykmeldingId)
        deleteSvar(connection, sykmeldingId)
        deleteSykmeldingstatus(connection, sykmeldingId)
        deleteSykmeldingsdokument(connection, sykmeldingId)
        deleteSykmeldingsopplysninger(connection, sykmeldingId)
        connection.commit()
    }
}

private fun deleteSykmeldingsopplysninger(connection: Connection, sykmeldingId: String): Boolean {
    return connection.prepareStatement("""
            delete from sykmeldingsopplysninger where id = ? 
        """).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSykmeldingsdokument(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement("""
            delete from sykmeldingsdokument where id = ? 
        """).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSykmeldingstatus(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement("""
            delete from sykmeldingstatus where sykmelding_id = ? 
        """).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSvar(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement("""
            delete from svar where sykmelding_id = ? 
        """).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteArbeidsgiver(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement("""
            delete from arbeidsgiver where sykmelding_id = ?
        """).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteBehandlingsutfall(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement(
            """delete from behandlingsutfall where id = ?"""
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}
