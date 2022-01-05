package no.nav.syfo.azuread.v2

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AzureAdV2Cache {
    companion object {
        private val log = LoggerFactory.getLogger(AzureAdV2Cache::class.java)
    }

    private val cache = Caffeine
        .newBuilder().expireAfterWrite(Duration.ofHours(1))
        .maximumSize(500)
        .build<String, AzureAdV2Token>()

    fun getToken(token: String): AzureAdV2Token? {
        val key = getSha256Key(token)
        return cache.getIfPresent(key)?.let {
            when (it.expires.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                true -> cache.invalidate(key).let {
                    log.info("Token in cache has expired")
                    null
                }
                else -> it
            }
        }
    }

    fun putValue(token: String, azureAdV2Token: AzureAdV2Token): AzureAdV2Token {
        cache.put(getSha256Key(token), azureAdV2Token)
        return azureAdV2Token
    }

    private fun getSha256Key(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
}
