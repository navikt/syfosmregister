package no.nav.syfo.sykmelding.papir.api

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.request.*
import io.ktor.client.request.headers
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PapirsykmeldingApiKtTest {
    val database = TestDB.database
    val sykmeldingerService = PapirsykmeldingService(database)

    @BeforeEach
    fun beforeTest() {
        database.connection.dropData()
        val sykmelding = testSykmeldingsdokument.sykmelding
        runBlocking {
            database.lagreMottattSykmelding(
                testSykmeldingsopplysninger,
                testSykmeldingsdokument.copy(
                    sykmelding =
                        sykmelding.copy(
                            avsenderSystem = AvsenderSystem("Papirsykmelding", "1.0"),
                        ),
                ),
            )
        }
    }

    companion object {
        @AfterAll
        @JvmStatic
        internal fun tearDown() {
            TestDB.stop()
        }
    }

    @Test
    internal fun `SykmeldingApiV2 papirsykmelding integration test Skal få unauthorized når credentials mangler`() {
        val sykmeldingerV2Uri = "api/v2/papirsykmelding"
        testApplication {
            setUpTestApplication()
            application {
                val path = "src/test/resources/jwkset.json"
                val uri = Paths.get(path).toUri().toURL()
                val jwkProvider = JwkProviderBuilder(uri).build()
                setupAuth(
                    jwkProvider,
                    "tokenXissuer",
                    jwkProvider,
                    getEnvironment(),
                )
                routing {
                    route("/api/v2") {
                        authenticate("azureadv2") {
                            registrerServiceuserPapirsykmeldingApi(
                                papirsykmeldingService = sykmeldingerService,
                            )
                        }
                    }
                }
            }

            val response = client.get("$sykmeldingerV2Uri/uuid")
            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `SykmeldingApiV2 papirsykmelding integration test Skal returnere papirsykmelding`() {
        val sykmeldingerV2Uri = "api/v2/papirsykmelding"
        testApplication {
            setUpTestApplication()
            application {
                val path = "src/test/resources/jwkset.json"
                val uri = Paths.get(path).toUri().toURL()
                val jwkProvider = JwkProviderBuilder(uri).build()
                setupAuth(
                    jwkProvider,
                    "tokenXissuer",
                    jwkProvider,
                    getEnvironment(),
                )
                routing {
                    route("/api/v2") {
                        authenticate("azureadv2") {
                            registrerServiceuserPapirsykmeldingApi(
                                papirsykmeldingService = sykmeldingerService,
                            )
                        }
                    }
                }
            }

            val response =
                client.get("$sykmeldingerV2Uri/uuid") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    issuer = "assureissuer",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmelding =
                objectMapper.readValue(
                    response.bodyAsText(),
                    PapirsykmeldingDTO::class.java,
                )
            sykmelding shouldNotBe null
            sykmelding.sykmelding.id shouldBeEqualTo "uuid"
        }
    }
}
