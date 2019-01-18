package no.nav.syfo.vault

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariDataSource
import com.bettercloud.vault.Vault
import com.zaxxer.hikari.HikariConfig
import org.slf4j.LoggerFactory
import java.util.TimerTask

class HikariCPVaultUtil constructor(private val hikariConfig: HikariConfig, private val vault: Vault, private val mountPath: String, private val role: String) {

    private var ds: HikariDataSource? = null

    private fun setDs(ds: HikariDataSource) {
        this.ds = ds
    }

    @Throws(VaultException::class)
    private fun refreshCredentialsAndReturnRefreshInterval(): RefreshResult {
        val path = "$mountPath/creds/$role"
        log.info("Renewing database credentials for role \"$role\"")
        val response = vault.logical().read(path)
        val username = response.data["username"]
        val password = response.data["password"]
        log.info("Got new credentials (username=$username)")

        hikariConfig.username = username
        hikariConfig.password = password
        if (ds != null) {
            ds!!.username = username
            ds!!.password = password
        }

        return RefreshResult(response.leaseId, response.leaseDuration!!)
    }

    private class RefreshResult internal constructor(internal val leaseId: String, internal val leaseDuration: Long)

    companion object {
        private val log = LoggerFactory.getLogger(HikariCPVaultUtil::class.java)

        @Throws(VaultError::class)
        fun createHikariDataSourceWithVaultIntegration(config: HikariConfig, mountPath: String, role: String): HikariDataSource {
            val instance = VaultUtil().init() as VaultUtil
            val vault: Vault = instance.client

            val hikariCPVaultUtil = HikariCPVaultUtil(config, vault, mountPath, role)

            class RefreshDbCredentialsTask : TimerTask() {
                override fun run() {
                    try {
                        val refreshResult = hikariCPVaultUtil.refreshCredentialsAndReturnRefreshInterval()
                        instance.timer.schedule(RefreshDbCredentialsTask(), VaultUtil.suggestedRefreshInterval(refreshResult.leaseDuration * 1000))
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
                val refreshResult = hikariCPVaultUtil.refreshCredentialsAndReturnRefreshInterval()
                instance.timer.schedule(RefreshDbCredentialsTask(), VaultUtil.suggestedRefreshInterval(refreshResult.leaseDuration * 1000))
            } catch (e: VaultException) {
                throw VaultError("Could not fetch database credentials for role \"$role\"", e)
            }

            val ds = HikariDataSource(config)
            hikariCPVaultUtil.setDs(ds)

            return ds
        }
    }
}