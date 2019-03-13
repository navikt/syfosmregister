package no.nav.syfo.db

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.ApplicationConfig
import no.nav.syfo.ApplicationState
import no.nav.syfo.vault.runRenewTokenTask
import no.nav.syfo.vault.suggestedRefreshIntervalInMillis
import no.nav.syfo.vault.vaultClient
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

private val log = LoggerFactory.getLogger("no.nav.syfo.db")
private val dispatcher: CoroutineContext = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

private data class VaultDBCredentials(
    val leaseId: String,
    val leaseDuration: Long,
    val username: String,
    val password: String
)

private enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

class Database(private val config: ApplicationConfig, private val applicationState: ApplicationState) {
    lateinit var connection: Connection

    suspend fun init() {
        coroutineScope {
            launch { runRenewTokenTask() }
            Flyway.configure().run {
                val credentials = getNewCredentials(
                        mountPath = config.mountPathVault,
                        databaseName = config.databaseName,
                        role = Role.ADMIN
                )
                dataSource(config.syfosmregisterDBURL, credentials.username, credentials.password)
                initSql("SET ROLE \"${config.databaseName}-${Role.ADMIN}\"") // required for assigning proper owners for the tables
                load().migrate()
            }
            val initialCredentials = getNewCredentials(
                    mountPath = config.mountPathVault,
                    databaseName = config.databaseName,
                    role = Role.USER
            )
            val hikariDataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = config.syfosmregisterDBURL
                username = initialCredentials.username
                password = initialCredentials.password
                maximumPoolSize = 2
                minimumIdle = 0
                idleTimeout = 10001
                connectionTimeout = 250
                maxLifetime = 30001
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            })
            connection = hikariDataSource.connection

            launch {
                delay(suggestedRefreshIntervalInMillis(initialCredentials.leaseDuration * 1000))
                runRenewCredentialsTask(
                        dataSource = hikariDataSource,
                        mountPath = config.mountPathVault,
                        databaseName = config.databaseName,
                        role = Role.USER
                ) { applicationState.running }
            }
        }
    }
}

private fun getNewCredentials(mountPath: String, databaseName: String, role: Role): VaultDBCredentials {
    val path = "$mountPath/creds/$databaseName-$role"
    log.debug("Getting database credentials for path '$path'")
    try {
        val response = vaultClient.logical().read(path)
        val username = checkNotNull(response.data["username"]) { "Username is not set in response from Vault" }
        val password = checkNotNull(response.data["password"]) { "Password is not set in response from Vault" }
        log.debug("Got new credentials (username=$username, leaseDuration=${response.leaseDuration})")
        return VaultDBCredentials(response.leaseId, response.leaseDuration, username, password)
    } catch (e: VaultException) {
        when (e.httpStatusCode) {
            403 -> log.error("Vault denied permission to fetch database credentials for path '$path'", e)
            else -> log.error("Could not fetch database credentials for path '$path'", e)
        }
        throw e
    }
}

private suspend fun runRenewCredentialsTask(
    dataSource: HikariDataSource,
    mountPath: String,
    databaseName: String,
    role: Role,
    condition: () -> Boolean
) {
    while (condition()) {
        val credentials = getNewCredentials(mountPath, databaseName, role)
        dataSource.apply {
            hikariConfigMXBean.setUsername(credentials.username)
            hikariConfigMXBean.setPassword(credentials.password)
        }
        delay(suggestedRefreshIntervalInMillis(credentials.leaseDuration * 1000))
    }
}

    suspend fun <T> dbQuery(block: Connection.() -> T): T = withContext(dispatcher) {
        Database.connection
        transaction { block() }
    }