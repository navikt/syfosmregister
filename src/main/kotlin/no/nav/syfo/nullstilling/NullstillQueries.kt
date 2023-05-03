package no.nav.syfo.nullstilling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import java.sql.Connection

suspend fun DatabaseInterface.slettSykmelding(sykmeldingId: String) = withContext(Dispatchers.IO) {
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

private suspend fun deleteSykmeldingsopplysninger(connection: Connection, sykmeldingId: String): Boolean = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """
            delete from sykmeldingsopplysninger where id = ? 
        """,
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private suspend fun deleteSykmeldingsdokument(connection: Connection, sykmeldingId: String) = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """
            delete from sykmeldingsdokument where id = ? 
        """,
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private suspend fun deleteSykmeldingstatus(connection: Connection, sykmeldingId: String) = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """
            delete from sykmeldingstatus where sykmelding_id = ? 
        """,
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private suspend fun deleteSvar(connection: Connection, sykmeldingId: String) = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """
            delete from svar where sykmelding_id = ? 
        """,
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private suspend fun deleteArbeidsgiver(connection: Connection, sykmeldingId: String) = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """
            delete from arbeidsgiver where sykmelding_id = ?
        """,
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}

private suspend fun deleteBehandlingsutfall(connection: Connection, sykmeldingId: String) = withContext(Dispatchers.IO) {
    connection.prepareStatement(
        """delete from behandlingsutfall where id = ?""",
    ).use {
        it.setString(1, sykmeldingId)
        it.execute()
    }
}
