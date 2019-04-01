package no.nav.syfo.db

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.vault.Vault
import no.nav.syfo.vault.Vault.suggestedRefreshIntervalInMillis
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet

private val log = LoggerFactory.getLogger("no.nav.syfo.db")

private data class VaultCredentials(
    val leaseId: String,
    val leaseDuration: Long,
    val username: String,
    val password: String
)

data class RenewCredentialsTaskData(
    val initialDelay: Long,
    val dataSource: HikariDataSource,
    val mountPath: String,
    val databaseName: String,
    val role: Role
)

enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

class Database(private val env: Environment) {
    private val dataSource: HikariDataSource
    private val renewCredentialsTaskData: RenewCredentialsTaskData

    val connection: Connection
        get() = dataSource.connection

    init {
        runFlywayMigrations()

        val initialCredentials = getNewCredentials(
                mountPath = env.mountPathVault,
                databaseName = env.databaseName,
                role = Role.USER
        )
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = env.syfosmregisterDBURL
            username = initialCredentials.username
            password = initialCredentials.password
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        renewCredentialsTaskData = RenewCredentialsTaskData(
                initialDelay = suggestedRefreshIntervalInMillis(initialCredentials.leaseDuration * 1000),
                dataSource = dataSource,
                mountPath = env.mountPathVault,
                databaseName = env.databaseName,
                role = Role.USER
        )
    }

    private fun runFlywayMigrations() = Flyway.configure().run {
        val credentials = getNewCredentials(
                mountPath = env.mountPathVault,
                databaseName = env.databaseName,
                role = Role.ADMIN
        )
        dataSource(env.syfosmregisterDBURL, credentials.username, credentials.password)
        initSql("SET ROLE \"${env.databaseName}-${Role.ADMIN}\"") // required for assigning proper owners for the tables
        load().migrate()
    }

    private fun getNewCredentials(mountPath: String, databaseName: String, role: Role): VaultCredentials {
        val path = "$mountPath/creds/$databaseName-$role"
        log.debug("Getting database credentials for path '$path'")
        try {
            val response = Vault.client.logical().read(path)
            val username = checkNotNull(response.data["username"]) { "Username is not set in response from Vault" }
            val password = checkNotNull(response.data["password"]) { "Password is not set in response from Vault" }
            log.debug("Got new credentials (username=$username, leaseDuration=${response.leaseDuration})")
            return VaultCredentials(response.leaseId, response.leaseDuration, username, password)
        } catch (e: VaultException) {
            when (e.httpStatusCode) {
                403 -> log.error("Vault denied permission to fetch database credentials for path '$path'", e)
                else -> log.error("Could not fetch database credentials for path '$path'", e)
            }
            throw e
        }
    }

    suspend fun runRenewCredentialsTask(condition: () -> Boolean) {
        delay(renewCredentialsTaskData.initialDelay)
        while (condition()) {
            val credentials = getNewCredentials(renewCredentialsTaskData.mountPath,
                    renewCredentialsTaskData.databaseName, renewCredentialsTaskData.role)
            renewCredentialsTaskData.dataSource.apply {
                hikariConfigMXBean.setUsername(credentials.username)
                hikariConfigMXBean.setPassword(credentials.password)
                hikariPoolMXBean.softEvictConnections()
            }
            delay(suggestedRefreshIntervalInMillis(credentials.leaseDuration * 1000))
        }
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}
