package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.nio.file.Paths
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.setupAuth
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.updateMottattSykmelding
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.UtenlandskSykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SykmeldingApiV2IntegrationTest {
    val sykmeldingerV2Uri = "api/v3/sykmeldinger"

    val database = TestDB.database
    val sykmeldingerService = SykmeldingerService(database)

    @BeforeEach
    fun beforeTest() {
        runBlocking {
            database.connection.dropData()
            database.lagreMottattSykmelding(
                testSykmeldingsopplysninger,
                testSykmeldingsdokument,
            )
            database.registerStatus(
                SykmeldingStatusEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                    StatusEvent.APEN,
                ),
            )
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        }
    }

    @AfterEach
    fun afterTest() {
        TestDB.stop()
    }

    @Test
    internal fun `SykmeldingApiV2 integration test skal få unauthorized når credentials mangler`() {
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
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(
                                sykmeldingerService = sykmeldingerService,
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
    internal fun `SykmeldingApiV2 integration test henter sykmelding når fnr stemmer med sykmeldingen`() {
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
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(
                                sykmeldingerService = sykmeldingerService,
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
                                    subject = "pasientFnr",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmelding =
                objectMapper.readValue(response.bodyAsText(), SykmeldingDTO::class.java)
            sykmelding.utenlandskSykmelding shouldBeEqualTo null
        }
    }

    @Test
    internal fun `SykmeldingApiV2 integration test Får NotFound med feil fnr, hvor sykmelding finnes i db`() {
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
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(
                                sykmeldingerService = sykmeldingerService,
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
                                    subject = "feilFnr",
                                )
                            }",
                        )
                    }
                }
            response.status shouldBeEqualTo HttpStatusCode.NotFound
        }
    }

    @Test
    internal fun `SykmeldingApiV2 integration test Får NotFound med id som ikke finnes i databasen`() {
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
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(
                                sykmeldingerService = sykmeldingerService,
                            )
                        }
                    }
                }
            }

            val response =
                client.get("$sykmeldingerV2Uri/annenId") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "pasientFnr",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.NotFound
        }
    }

    @Test
    internal fun `SykmeldingApiV2 integration test Skal hente utenlandsk sykmelding`() {
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
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(
                                sykmeldingerService = sykmeldingerService,
                            )
                        }
                    }
                }
            }

            database.updateMottattSykmelding(
                testSykmeldingsopplysninger.copy(
                    utenlandskSykmelding = UtenlandskSykmelding("Utland", false),
                ),
                testSykmeldingsdokument,
            )
            val response =
                client.get("$sykmeldingerV2Uri/uuid") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "pasientFnr",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmelding =
                objectMapper.readValue(response.bodyAsText(), SykmeldingDTO::class.java)
            sykmelding.utenlandskSykmelding shouldBeEqualTo UtenlandskSykmeldingDTO("Utland")
        }
    }
}
