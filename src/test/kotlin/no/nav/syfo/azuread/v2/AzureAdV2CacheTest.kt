package no.nav.syfo.azuread.v2

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AzureAdV2CacheTest : Spek({

    val azureAdCache = AzureAdV2Cache()
    describe("Test caching") {
        it("Test get null") {
            azureAdCache.getToken("mykey") shouldBeEqualTo null
        }
        it("Save and get token") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getToken("token")
            cache shouldBeEqualTo azureAdToken
        }
        it("If cached token is expried, should get null and invalidate cache") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getToken("token")
            cache shouldBeEqualTo null
        }
    }
})
