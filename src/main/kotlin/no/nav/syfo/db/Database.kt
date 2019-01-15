package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import no.nav.syfo.Environment
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.coroutines.CoroutineContext

@ObsoleteCoroutinesApi
object Database {
    private val dispatcher: CoroutineContext = newFixedThreadPoolContext(4, "database-pool")

    fun init(environment: Environment) {
        Flyway.configure().run {
            dataSource(environment.databaseUrl, environment.databaseUsername, environment.databasePassword)
            load().migrate()
        }
        Database.connect(hikari(environment))
        transaction {
            addLogger(StdOutSqlLogger)
            create(
                    ReceivedSykmelding
            )
        }
    }

    fun hikari(environment: Environment) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = environment.databaseUrl
        username = environment.databaseUsername
        password = environment.databasePassword
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    })

    suspend fun <T> dbQuery(block: () -> T): T = withContext(dispatcher) {
        transaction { block() }
    }
}
