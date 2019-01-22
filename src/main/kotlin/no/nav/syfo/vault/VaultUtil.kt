package no.nav.syfo.vault

import java.nio.file.Paths
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.Vault
import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.VaultConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.Timer
import java.util.TimerTask

data class VaultError(override val message: String, override val cause: Throwable) : Exception(message, cause)
data class PostgresDBUsernamePassword(val username: String, val password: String)
private val log = LoggerFactory.getLogger("nav.syfo.vault.VaultUtil")

fun postgresDBUsernamePassword(): PostgresDBUsernamePassword {
    val timer = Timer("VaultScheduler", true)

    val vaultConfig =
            try { VaultConfig()
                    .address("https://vault.adeo.no")
                    .token(getVaultToken())
                    .openTimeout(5)
                    .readTimeout(30)
                    .sslConfig(SslConfig().build())
                    .build()
            } catch (e: Exception) {
                throw VaultError("Could not instantiate the Vault REST client", e)
            }

    val vaultClient = Vault(vaultConfig)

    log.info("VaultClient Vault REST client OK")

    log.info("Verify that the token is ok")
    // Verify that the token is ok
    val lookupSelf =
            try {
                vaultClient.auth().lookupSelf()
            } catch (e: VaultException) {
                if (e.httpStatusCode == 403) {
                    throw VaultError("The application's vault token seems to be invalid", e)
                } else {
                    throw VaultError("Could not validate the application's vault token", e)
                }
            }

    if (lookupSelf!!.isRenewable) {
        class RefreshTokenTask : TimerTask() {
            override fun run() {
                try {
                    log.info("Refreshing Vault token (TTL = " + vaultClient.auth().lookupSelf().ttl + " seconds)")
                    val response = vaultClient.auth().renewSelf()
                    timer.schedule(RefreshTokenTask(), suggestedRefreshInterval(response.authLeaseDuration * 1000))
                } catch (e: VaultException) {
                    log.error("Could not refresh the Vault token", e)
                }
            }
        }
        timer.schedule(RefreshTokenTask(), suggestedRefreshInterval(lookupSelf.ttl * 1000))
    }

    /* TODO val mountPath = if (getEnvironmentClass() === P)
                "postgresql/prod-fss"
            else
                "postgresql/preprod-fss"
            */

    val path = "postgresql/preprod-fss/creds/syfosmregister-admin"
    val response = vaultClient.logical().read(path)
    val postgressDBUsername = response.data["username"].orEmpty()
    val postgressDBPassword = response.data["password"].orEmpty()
    if (postgressDBUsername.isNotEmpty() && postgressDBPassword.isNotEmpty()) {
        return PostgresDBUsernamePassword(postgressDBUsername, postgressDBPassword)
    } else {
        throw RuntimeException("VaultClient dosent contain username and or password")
    }
}

fun getVaultToken(): String {
    val vaultTokenProperty = "VAULT_TOKEN"
    val vaultTokenPath = "VAULT_TOKEN_PATH"

    try {
        val env = HashMap(System.getenv())
        System.getProperties().forEach { key, value ->
            if (value is String) {
                env[key as String] = value
            }
        }
        return if (!env[vaultTokenProperty].isNullOrBlank()) {
            log.info("Token from: VAULT_TOKEN")
            env[vaultTokenProperty].toString()
        } else if (!env[vaultTokenPath].isNullOrBlank()) {
            log.info("Token from: VAULT_TOKEN_PATH")
            val encoded = Files.readAllBytes(Paths.get(env[vaultTokenPath]))
            String(encoded, Charsets.UTF_8).trim { it <= ' ' }
        } else if (Files.exists(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))) {
            log.info("Token from: var/run/secrets/nais.io/vault/vault_token")
            val encoded = Files.readAllBytes(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))
            String(encoded, Charsets.UTF_8).trim { it <= ' ' }
        } else {
            throw RuntimeException("Neither VAULT_TOKEN or VAULT_TOKEN_PATH is set")
        }
    } catch (e: Exception) {
        throw RuntimeException("Could not get a vault token for authentication", e)
    }
}

fun suggestedRefreshInterval(duration: Long): Long {
    return if (duration < 60000) {
        duration / 2
    } else {
        duration - 30000
    }
}