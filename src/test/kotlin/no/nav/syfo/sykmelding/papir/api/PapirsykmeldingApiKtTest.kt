package no.nav.syfo.sykmelding.papir.api

import com.auth0.jwk.JwkProviderBuilder
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import java.nio.file.Paths
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

class PapirsykmeldingApiKtTest :
    FunSpec({
        val database = TestDB.database
        val sykmeldingerService = PapirsykmeldingService(database)

        beforeTest {
            database.connection.dropData()
            val sykmelding = testSykmeldingsdokument.sykmelding
            database.lagreMottattSykmelding(
                testSykmeldingsopplysninger,
                testSykmeldingsdokument.copy(
                    sykmelding =
                        sykmelding.copy(avsenderSystem = AvsenderSystem("Papirsykmelding", "1.0"))
                )
            )
        }
        afterSpec { TestDB.stop() }

        context("SykmeldingApiV2 papirsykmelding integration test") {
            val sykmeldingerV2Uri = "api/v2/papirsykmelding"
            with(TestApplicationEngine()) {
                val path = "src/test/resources/jwkset.json"
                val uri = Paths.get(path).toUri().toURL()
                val jwkProvider = JwkProviderBuilder(uri).build()
                setUpTestApplication()
                application.setupAuth(
                    jwkProvider,
                    "tokenXissuer",
                    jwkProvider,
                    getEnvironment(),
                )
                application.routing {
                    route("/api/v2") {
                        authenticate("azureadv2") {
                            registrerServiceuserPapirsykmeldingApi(
                                papirsykmeldingService = sykmeldingerService
                            )
                        }
                    }
                }

                test("Skal få unauthorized når credentials mangler") {
                    with(handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {}) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                test("Skal returnere papirsykmelding") {
                    with(
                        handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                            addHeader(
                                HttpHeaders.Authorization,
                                "Bearer ${generateJWT("syfosoknad", "clientid", issuer = "assureissuer")}",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val sykmelding =
                            objectMapper.readValue(response.content, PapirsykmeldingDTO::class.java)
                        sykmelding shouldNotBe null
                        sykmelding.sykmelding.id shouldBeEqualTo "uuid"
                    }
                }
            }
        }
    })
