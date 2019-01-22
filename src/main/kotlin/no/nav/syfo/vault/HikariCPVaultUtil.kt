package no.nav.syfo.vault

import com.zaxxer.hikari.HikariDataSource
import com.bettercloud.vault.Vault
import com.zaxxer.hikari.HikariConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("nav.syfo.vault.HikariCPVaultUtil")
data class RefreshResult internal constructor(internal val leaseId: String, internal val leaseDuration: Long)

class HikariCPVaultUtil constructor(private val hikariConfig: HikariConfig, private val mountPath: String, private val role: String) {
    // val hikariDataSource = createHikariDataSourceWithVaultIntegration(hikariConfig, mountPath, role)
}

fun refreshCredentialsAndReturnRefreshInterval(mountPath: String, role: String, vault: Vault, hikariConfig: HikariConfig, hikariDataSource: HikariDataSource): RefreshResult {
    val path = "$mountPath/creds/syfosmregister-$role"
    log.info("Renewing database credentials for role \"$role\"")
    val response = vault.logical().read(path)
    val username = response.data["username"]
    val password = response.data["password"]
    log.info("Got new credentials (username=$username)")

    hikariConfig.username = username
    hikariConfig.password = password

    hikariDataSource.username = username
    hikariDataSource.password = password

    return RefreshResult(response.leaseId, response.leaseDuration!!)
}

/*
fun createHikariDataSourceWithVaultIntegration(hikariConfig: HikariConfig, mountPath: String, role: String): HikariDataSource {
    val vaultUtil = VaultUtil()
    val hikariCPVaultUtil = HikariCPVaultUtil(hikariConfig, mountPath, role)

    class RefreshDbCredentialsTask : TimerTask() {
        override fun run() {
            try {
                val refreshResult = refreshCredentialsAndReturnRefreshInterval(mountPath, role, vaultUtil.vaultClient, hikariConfig, hikariCPVaultUtil.hikariDataSource)
                vaultUtil.timer.schedule(RefreshDbCredentialsTask(), suggestedRefreshInterval(refreshResult.leaseDuration * 1000))
            } catch (e: VaultException) {
                // We cannot re-throw exceptions (since this task runs in its own thread), so we log them instead.
                if (e.httpStatusCode == 403) {
                    log.error("Vault denied permission to fetch database credentials for role \"$role\"", e)
                } else {
                    log.error("Could not fetch database credentials for role \"$role\"", e)
                }
            }
        }
    }

    // The first time we fetch credentials, we can rethrow an exception if we get one - fail fast!
    try {
        val refreshResult = refreshCredentialsAndReturnRefreshInterval(mountPath, role, vaultUtil.vaultClient, hikariConfig, hikariCPVaultUtil.hikariDataSource)
        vaultUtil.timer.schedule(RefreshDbCredentialsTask(), suggestedRefreshInterval(refreshResult.leaseDuration * 1000))
    } catch (e: VaultException) {
        throw VaultError("Could not fetch database credentials for role \"$role\"", e)
    }

    return HikariDataSource(hikariConfig)
}
        */