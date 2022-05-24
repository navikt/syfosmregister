package no.nav.syfo.sykmelding.papir.api

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.setupAuth
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.sykmelding.papir.PapirsykmeldingService
import no.nav.syfo.sykmelding.papir.model.PapirsykmeldingDTO
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

class PapirsykmeldingApiKtTest : Spek({

    val database = TestDB()
    val sykmeldingerService = PapirsykmeldingService(database)

    beforeEachTest {
        val sykmelding = testSykmeldingsdokument.sykmelding
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument.copy(sykmelding = sykmelding.copy(avsenderSystem = AvsenderSystem("Papirsykmelding", "1.0"))))
    }
    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("SykmeldingApiV2 papirsykmelding integration test") {
        val sykmeldingerV2Uri = "api/v2/papirsykmelding"
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(
                listOf("clientId"),
                jwkProvider,
                jwkProvider,
                "me",
                "me",
                jwkProvider,
                getEnvironment()
            )
            application.routing {
                route("/api/v2") {
                    authenticate("azureadv2") {
                        registrerServiceuserPapirsykmeldingApi(papirsykmeldingService = sykmeldingerService)
                    }
                }
            }

            it("Skal få unauthorized når credentials mangler") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {}) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("Skal returnere papirsykmelding") {

                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientid", issuer = "assureissuer")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmelding = objectMapper.readValue(response.content, PapirsykmeldingDTO::class.java)
                    sykmelding shouldNotBe null
                    sykmelding.sykmelding.id shouldBeEqualTo "uuid"
                }
            }
        }
    }
})
