package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.db.DatabaseInterface
import org.flywaydb.core.Flyway
import java.sql.Connection

object TestDB : DatabaseInterface {
    private val pg: EmbeddedPostgres = EmbeddedPostgres.start()
    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }


    init {
        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun cleanUp() {
        pg.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM sykmelding_metadata").executeUpdate()
        connection.prepareStatement("DELETE FROM sykmelding").executeUpdate()
        connection.commit()
    }
}
