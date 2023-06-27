package no.nav.syfo.sykmelding.internal.tilgang

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import java.time.OffsetDateTime
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2Token
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldBeEqualTo

class TilgangskontrollServiceTest :
    FunSpec({
        val httpClientTest = HttpClientTest()
        val azureadClient = mockk<AzureAdV2Client>(relaxed = true)
        val tilgangskontrollService =
            TilgangskontrollService(
                azureadClient,
                httpClientTest.httpClient,
                "/api/v1/",
                "clientId"
            )
        coEvery { azureadClient.getOnBehalfOfToken(any(), any()) } returns
            AzureAdV2Token("token", OffsetDateTime.now().plusSeconds(3599))
        mockkStatic("io.ktor.client.request.BuildersKt")

        context("Test TilgangskontrollService") {
            test("Should get false when user not have access") {
                httpClientTest.responseData =
                    ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false)))
                val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123")
                tilgang shouldBeEqualTo false
            }
            test("Should get true when user have access") {
                httpClientTest.responseData =
                    ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
                val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "bearer 123")
                tilgang shouldBeEqualTo true
            }
            test("Should get false when services returns 401 Unauthorized") {
                httpClientTest.responseData =
                    ResponseData(HttpStatusCode.Unauthorized, "Unauthorized")
                val tilgang = tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123")
                tilgang shouldBeEqualTo false
            }
            test("Should get false when service returns 403 forbidden") {
                httpClientTest.responseData = ResponseData(HttpStatusCode.Forbidden, "Forbidden")
                tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123") shouldBeEqualTo
                    false
            }
            test("Should get false when service returns 500 internal servver error") {
                httpClientTest.responseData =
                    ResponseData(HttpStatusCode.InternalServerError, "Internal Server Errror")
                tilgangskontrollService.hasAccessToUserOboToken("123", "Bearer 123") shouldBeEqualTo
                    false
            }
        }
    })
