package no.nav.syfo.vault

import java.nio.file.Paths
import com.bettercloud.vault.VaultException
import com.bettercloud.vault.response.LookupResponse
import com.bettercloud.vault.Vault
import com.bettercloud.vault.SslConfig
import com.bettercloud.vault.VaultConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.Timer
import java.util.TimerTask

class VaultUtil {
    lateinit var client: Vault
    val timer: Timer = Timer("VaultScheduler", true)

    @Throws(VaultError::class)
    fun init() {
        val vaultConfig: VaultConfig?
        try {
            vaultConfig = VaultConfig()
                    .address((System.getenv() as Map<String, String>).getOrDefault("VAULT_ADDR", "https://vault.adeo.no"))
                    .token(vaultToken)
                    .openTimeout(5)
                    .readTimeout(30)
                    .sslConfig(SslConfig().build())
                    .build()
        } catch (e: VaultException) {
            throw VaultError("Could not instantiate the Vault REST client", e)
        }

        client = Vault(vaultConfig)

        // Verify that the token is ok
        val lookupSelf: LookupResponse?
        try {
            lookupSelf = client.auth().lookupSelf()
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
                        log.info("Refreshing Vault token (old TTL = " + client.auth().lookupSelf().ttl + " seconds)")
                        val response = client.auth().renewSelf()
                        log.info("Refreshed Vault token (new TTL = " + client.auth().lookupSelf().ttl + " seconds)")
                        timer.schedule(RefreshTokenTask(), suggestedRefreshInterval(response.authLeaseDuration * 1000))
                    } catch (e: VaultException) {
                        log.error("Could not refresh the Vault token", e)
                    }
                }
            }
            log.info("Starting a refresh timer on the vault token (TTL = " + lookupSelf.ttl + " seconds")
            timer.schedule(RefreshTokenTask(), suggestedRefreshInterval(lookupSelf.ttl * 1000))
        } else {
            log.warn("Vault token is not renewable")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(VaultUtil::class.java)

        val VAULT_TOKEN_PROPERTY = "VAULT_TOKEN"
        val VAULT_TOKEN_PATH = "VAULT_TOKEN_PATH"

        private var INSTANCE: VaultUtil? = null

        // We should refresh tokens from Vault before they expire, so we add 30 seconds margin.
        // If the token is valid for less than 60 seconds, we use duration / 2 instead.
        fun suggestedRefreshInterval(duration: Long): Long {
            return if (duration < 60000) {
                duration / 2
            } else {
                duration - 30000
            }
        }

        // might throw an exception
        val instance: VaultUtil
            @Throws(VaultError::class)
            get() {
                if (INSTANCE == null) {
                    val util = VaultUtil()
                    util.init()
                    INSTANCE = util
                }
                return INSTANCE as VaultUtil
            }

        private val vaultToken: String?
            get() {
                try {
                    val env = HashMap(System.getenv())
                    System.getProperties().forEach { key, value ->
                        if (value is String) {
                            env[key as String] = value
                        }
                    }
                    if (!env[VAULT_TOKEN_PROPERTY].isNullOrBlank()) {
                        return env[VAULT_TOKEN_PROPERTY]
                    } else if (!env[VAULT_TOKEN_PATH].isNullOrBlank()) {
                        val encoded = Files.readAllBytes(Paths.get(env[VAULT_TOKEN_PATH]))
                        return String(encoded, Charsets.UTF_8).trim { it <= ' ' }
                    } else if (Files.exists(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))) {
                        val encoded = Files.readAllBytes(Paths.get("/var/run/secrets/nais.io/vault/vault_token"))
                        return String(encoded, Charsets.UTF_8).trim { it <= ' ' }
                    } else {
                        throw RuntimeException("Neither VAULT_TOKEN or VAULT_TOKEN_PATH is set")
                    }
                } catch (e: Exception) {
                    throw RuntimeException("Could not get a vault token for authentication", e)
                }
            }
    }
}