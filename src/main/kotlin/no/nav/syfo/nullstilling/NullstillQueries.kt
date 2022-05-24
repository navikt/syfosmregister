package no.nav.syfo.nullstilling

import no.nav.syfo.db.DatabaseInterface
import java.sql.Connection

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
    return connection.prepareStatement(
        """
            delete from sykmeldingsopplysninger where id = ? 
        """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSykmeldingsdokument(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement(
        """
            delete from sykmeldingsdokument where id = ? 
        """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSykmeldingstatus(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement(
        """
            delete from sykmeldingstatus where sykmelding_id = ? 
        """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteSvar(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement(
        """
            delete from svar where sykmelding_id = ? 
        """
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private fun deleteArbeidsgiver(connection: Connection, sykmeldingId: String) {
    connection.prepareStatement(
        """
            delete from arbeidsgiver where sykmelding_id = ?
        """
    ).use {
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
