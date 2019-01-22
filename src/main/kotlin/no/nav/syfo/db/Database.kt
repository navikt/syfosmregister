package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import no.nav.syfo.ApplicationConfig
import no.nav.syfo.vault.postgresDBUsernamePassword
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

    fun init(config: ApplicationConfig) {
        val hikari = hikari(config)
        Flyway.configure().run {
            dataSource(hikari.jdbcUrl, hikari.username, hikari.password)
            initSql(String.format("SET ROLE \"%s\"", "syfosmregister-admin"))
            load().migrate()
        }
        Database.connect(hikari(config))
        transaction {
            addLogger(StdOutSqlLogger)
            create(
                    ReceivedSykmelding
            )
        }
    }

    fun hikari(config: ApplicationConfig) = HikariDataSource(HikariConfig().apply {
        val postgresDBUsernamePassword = postgresDBUsernamePassword()
        username = postgresDBUsernamePassword.username
        password = postgresDBUsernamePassword.password
        jdbcUrl = config.syfosmregisterDBURL
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    })

    suspend fun <T> dbQuery(block: () -> T): T = withContext(dispatcher) {
        transaction { block() }
    }
}