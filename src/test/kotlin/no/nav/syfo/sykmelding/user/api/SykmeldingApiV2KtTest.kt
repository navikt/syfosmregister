package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import java.nio.file.Paths
import no.nav.syfo.application.setupAuth
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getSykmeldingDto
import no.nav.syfo.testutil.getVaultSecrets
import no.nav.syfo.testutil.setUpTestApplication
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingApiV2KtTest : Spek({

    val sykmeldingerV2Uri = "api/v2/sykmeldinger"

    val sykmeldingerService = mockkClass(SykmeldingerService::class)

    val mockPayload = mockk<Payload>()
    afterEachTest {
        clearAllMocks()
    }


    describe("Test sykmeldingApiV2") {

        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService) }
            it("Should get sykmeldinger for user") {
                every { sykmeldingerService.getUserSykmelding(any()) } returns listOf(getSykmeldingDto())
                every { mockPayload.subject } returns "123"
                with(handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
        }
    }

    describe("Test with autentication") {
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(getVaultSecrets().copy(loginserviceClientId = "clientId"), jwkProvider, "https://sts.issuer.net/myid", jwkProvider)
            application.routing { authenticate("jwt") { registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService) } }
            it("get sykmeldinger OK") {
                every { sykmeldingerService.getUserSykmelding(any()) } returns listOf(getSykmeldingDto())
                with(handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                    addHeader(HttpHeaders.Authorization,
                            "Bearer ${generateJWT("", "clientId", subject = "123")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
            it("Get sykmeldinger Unauthorized without JWT") {
                with(handleRequest(HttpMethod.Get, sykmeldingerV2Uri)) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Get sykmeldinger Unauthorized with incorrect audience") {
                with(handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                    addHeader("Authorization", "Bearer ${generateJWT("", "error", subject = "123")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
