package no.nav.syfo.azuread.v2

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AzureAdV2CacheTest : FunSpec({

    val azureAdCache = AzureAdV2Cache()
    context("Test caching") {
        test("Test get null") {
            azureAdCache.getToken("mykey") shouldBeEqualTo null
        }
        test("Save and get token") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getToken("token")
            cache shouldBeEqualTo azureAdToken
        }
        test("If cached token is expried, should get null and invalidate cache") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getToken("token")
            cache shouldBeEqualTo null
        }
    }
})
