package no.nav.syfo.azuread.v2

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class AzureAdV2CacheTest {
    private val azureAdCache = AzureAdV2Cache()

    @Test
    internal fun `Test caching Test get null`() {
        azureAdCache.getToken("mykey") shouldBeEqualTo null
    }

    @Test
    internal fun `Test caching Save and get token`() {
        val azureAdToken =
            AzureAdV2Token(
                "accessToken",
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10),
            )
        azureAdCache.putValue("token", azureAdToken)
        val cache = azureAdCache.getToken("token")
        cache shouldBeEqualTo azureAdToken
    }

    @Test
    internal fun `Test caching If cached token is expried, should get null and invalidate cache`() {
        val azureAdToken =
            AzureAdV2Token(
                "accessToken",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10),
            )
        azureAdCache.putValue("token", azureAdToken)
        val cache = azureAdCache.getToken("token")
        cache shouldBeEqualTo null
    }
}
