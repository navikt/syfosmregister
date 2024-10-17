package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2Token
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TilgangskontrollServiceTest {
    val httpClientTest = HttpClientTest()
    val azureadClient = mockk<AzureAdV2Client>(relaxed = true)
    val tilgangskontrollService =
        TilgangskontrollService(
            azureadClient,
            httpClientTest.httpClient,
            "/api/v1/",
            "clientId",
        )

    @BeforeEach
    fun beforeEach() {
        coEvery { azureadClient.getOnBehalfOfToken(any(), any()) } returns
            AzureAdV2Token("token", OffsetDateTime.now().plusSeconds(3599))
        mockkStatic("io.ktor.client.request.BuildersKt")
    }

    @Test
    internal fun `Test TilgangskontrollService Should get false when user not have access`() {
        httpClientTest.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false)))
        runBlocking {
            val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123")
            tilgang shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test TilgangskontrollService Should get true when user have access`() {
        httpClientTest.responseData =
            ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
        runBlocking {
            val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "bearer 123")
            tilgang shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Test TilgangskontrollService Should get false when services returns 401 Unauthorized`() {

        httpClientTest.responseData = ResponseData(HttpStatusCode.Unauthorized, "Unauthorized")
        runBlocking {
            val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123")
            tilgang shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test TilgangskontrollService Should get false when service returns 403 forbidden`() {
        httpClientTest.responseData = ResponseData(HttpStatusCode.Forbidden, "Forbidden")
        runBlocking {
            tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123") shouldBeEqualTo
                false
        }
    }

    @Test
    internal fun `Test TilgangskontrollService Should get false when service returns 500 internal servver error`() {
        runBlocking {
            httpClientTest.responseData =
                ResponseData(HttpStatusCode.InternalServerError, "Internal Server Errror")
            tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123") shouldBeEqualTo
                false
        }
    }
}
