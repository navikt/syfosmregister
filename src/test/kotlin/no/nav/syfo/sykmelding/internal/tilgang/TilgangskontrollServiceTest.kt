package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.http.HttpStatusCode
import io.ktor.util.InternalAPI
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@InternalAPI
class TilgangskontrollServiceTest : Spek({

    val httpClientTest = HttpClientTest()

    val tilgangskontrollService = TilgangskontrollService(httpClientTest.httpClient, "/api/v1/")
    mockkStatic("io.ktor.client.request.BuildersKt")

    describe("Test TilgangskontrollService") {
        it("Should get false when user not have access") {
            runBlocking {
                httpClientTest.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false, "har ikke tilgang")))
                val tilgang = tilgangskontrollService.hasAccessToUser("123", "Bearer 123")
                tilgang shouldEqual false
            }
        }
        it("Should get true when user have access") {
            runBlocking {
                httpClientTest.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true, "")))
                val tilgang = tilgangskontrollService.hasAccessToUser("123", "bearer 123")
                tilgang shouldEqual true
            }
        }
        it("Should get false when services returns 401 Unauthorized") {
            runBlocking {
                httpClientTest.responseData = ResponseData(HttpStatusCode.Unauthorized, "Unauthorized")
                val tilgang = tilgangskontrollService.hasAccessToUser("123", "Bearer 123")
                tilgang shouldEqual false
            }
        }

        it("Should get false when service returns 403 forbidden") {
            runBlocking {
                httpClientTest.responseData = ResponseData(HttpStatusCode.Forbidden, "Forbidden")
                tilgangskontrollService.hasAccessToUser("123", "Bearer 123") shouldEqual false
            }
        }
        it("Should get false when service returns 500 internal servver error") {
            runBlocking {
                httpClientTest.responseData = ResponseData(HttpStatusCode.InternalServerError, "Internal Server Errror")
                tilgangskontrollService.hasAccessToUser("123", "Bearer 123") shouldEqual false
            }
        }
    }
})
