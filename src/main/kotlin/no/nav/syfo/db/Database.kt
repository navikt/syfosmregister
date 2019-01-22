package no.nav.syfo.db

import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext

@ObsoleteCoroutinesApi
object Database {
    private val dispatcher: CoroutineContext = newFixedThreadPoolContext(4, "database-pool")
    /*
    fun init(config: ApplicationConfig) {
        val hikari = hikari(config)
        Flyway.configure().run {
            dataSource(hikari.jdbcUrl, hikari.username, hikari.password)
                // TODO  initSql(String.format("SET ROLE \"%s\"", dbRole("admin")))
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


    fun hikari(config: ApplicationConfig) = HikariCPVaultUtil(HikariConfig().apply {
        /* TODO val mountPath = if (getEnvironmentClass() === P)
            "postgresql/prod-fss"
        else
            "postgresql/preprod-fss"
        */
        jdbcUrl = config.syfosmregisterDBURL
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    },
            "postgresql/preprod-fss", "admin").hikariDataSource

    suspend fun <T> dbQuery(block: () -> T): T = withContext(dispatcher) {
        transaction { block() }
    }

    /* TODO
    private fun dbRole(role: String): String {
        return if (getEnvironmentClass() === P)
            arrayOf(APPLICATION_NAME, role).joinToString("-")
        else
            arrayOf(APPLICATION_NAME, requireEnvironmentName(), role).joinToString("-")
    }
    */
     */
}