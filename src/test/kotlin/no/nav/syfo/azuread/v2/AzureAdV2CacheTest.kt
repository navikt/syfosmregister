package no.nav.syfo.azuread.v2

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class AzureAdV2CacheTest : Spek({

    val azureAdCache = AzureAdV2Cache()
    describe("Test caching") {
        it("Test get null") {
            azureAdCache.getOboToken("mykey") shouldEqual null
        }
        it("Save and get token") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getOboToken("token")
            cache shouldEqual azureAdToken
        }
        it("If cached token is expried, should get null and invalidate cache") {
            val azureAdToken = AzureAdV2Token("accessToken", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10))
            azureAdCache.putValue("token", azureAdToken)
            val cache = azureAdCache.getOboToken("token")
            cache shouldEqual null
        }
    }
})
