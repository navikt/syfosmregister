package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.request.headers
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import java.nio.file.Paths
import java.time.LocalDate
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.getSykmeldingDto
import no.nav.syfo.testutil.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class SykmeldingApiV2KtTest {
    val sykmeldingerV2Uri = "api/v3/sykmeldinger"

    val sykmeldingerService = mockkClass(SykmeldingerService::class)

    @AfterEach
    fun afterTest() {
        clearAllMocks()
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get sykmeldinger for user with exclude filter`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }

            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns listOf(getSykmeldingDto())

            val response =
                client.get("$sykmeldingerV2Uri?exclude=APEN") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }
            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get sykmeldinger for user with include filter`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns listOf(getSykmeldingDto())

            val response =
                client.get("$sykmeldingerV2Uri?include=APEN") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }
            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get sykmeldinger for user with multiple exclude filters`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns listOf(getSykmeldingDto())

            val response =
                client.get("$sykmeldingerV2Uri?exclude=APEN&exclude=SENDT") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get bad request when exclude and include filters are in request`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any())
            } returns listOf(getSykmeldingDto())

            val response =
                client.get(
                    "$sykmeldingerV2Uri?exclude=APEN&exclude=SENDT&include=AVBRUTT",
                ) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }
            response.status shouldBeEqualTo HttpStatusCode.BadRequest
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get bad request when exclude filter is invalid`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any())
            } returns listOf(getSykmeldingDto())

            val response =
                client.get("$sykmeldingerV2Uri?exclude=ÅPEN") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get bad request when include filter is invalid`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any())
            } returns listOf(getSykmeldingDto())

            val response =
                client.get("$sykmeldingerV2Uri?include=ALL") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get sykmeldinger for user`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    null,
                    null,
                    any(),
                    any(),
                    any(),
                )
            } returns listOf(getSykmeldingDto())

            val response =
                client.get(sykmeldingerV2Uri) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }
            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should get sykmeldinger for user with FOM and TOM queryparams`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            val periode =
                getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 1, 20),
                    tom = LocalDate.of(2020, 2, 10),
                )
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    periode.fom,
                    periode.tom,
                    any(),
                    any(),
                    any(),
                )
            } returns
                listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode),
                    ),
                )

            val response =
                client.get("$sykmeldingerV2Uri?fom=2020-01-20&tom=2020-02-10") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.bodyAsText())
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should not get sykmeldinger with only FOM`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            val periode =
                getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 2, 11),
                    tom = LocalDate.of(2020, 2, 20),
                )
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    LocalDate.of(2020, 2, 20),
                    null,
                    any(),
                    any(),
                    any(),
                )
            } returns
                listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode),
                    ),
                )
            val response =
                client.get("$sykmeldingerV2Uri?fom=2020-02-20") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.bodyAsText())
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Should not get sykmeldinger with only TOM`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            val periode =
                getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 2, 11),
                    tom = LocalDate.of(2020, 2, 20),
                )
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    null,
                    LocalDate.of(2020, 2, 20),
                    any(),
                    any(),
                    any(),
                )
            } returns
                listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode),
                    ),
                )

            val response =
                client.get("$sykmeldingerV2Uri?tom=2020-02-20") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
            val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.bodyAsText())
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Test sykmeldingApiV2 Skal få Bad Requeset om TOM dato er før FOM dato`() {
        testApplication {
            setUpTestApplication()
            application {
                routing {
                    route("/api/v3") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            val response =
                client.get("$sykmeldingerV2Uri?fom=2020-05-20&tom=2020-02-10") {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "syfosoknad",
                                    "clientid",
                                    subject = "123",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
            response.bodyAsText() shouldBeEqualTo "FOM should be before or equal to TOM"
        }
    }

    @Test
    internal fun `Test with autentication get sykmeldinger OK`() {
        testApplication {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application {
                setupAuth(
                    jwkProviderTokenX = jwkProvider,
                    tokenXIssuer = "tokenXissuer",
                    jwkProviderAadV2 = jwkProvider,
                    environment = getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns listOf(getSykmeldingDto())

            val response =
                client.get(sykmeldingerV2Uri) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("", "clientid", subject = "123")}",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK
        }
    }

    @Test
    internal fun `Test with autentication get sykmeldinger Unauthorized for nivå 3`() {
        testApplication {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application {
                setupAuth(
                    jwkProviderTokenX = jwkProvider,
                    tokenXIssuer = "tokenXissuer",
                    jwkProviderAadV2 = jwkProvider,
                    environment = getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }
            coEvery {
                sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any())
            } returns listOf(getSykmeldingDto())

            val response =
                client.get(sykmeldingerV2Uri) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "",
                                    "clientid",
                                    subject = "123",
                                    level = "Level3",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `Test with autentication Get sykmeldinger Unauthorized without JWT`() {
        testApplication {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application {
                setupAuth(
                    jwkProviderTokenX = jwkProvider,
                    tokenXIssuer = "tokenXissuer",
                    jwkProviderAadV2 = jwkProvider,
                    environment = getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }

            val response = client.get(sykmeldingerV2Uri)
            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `Test with autentication Get sykmeldinger Unauthorized with incorrect audience`() {
        testApplication {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application {
                setupAuth(
                    jwkProviderTokenX = jwkProvider,
                    tokenXIssuer = "tokenXissuer",
                    jwkProviderAadV2 = jwkProvider,
                    environment = getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }

            val response =
                client.get(sykmeldingerV2Uri) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("", "error", subject = "123")}",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }

    @Test
    internal fun `Test with autentication Get sykmeldinger Unauthorized with incorrect issuer`() {
        testApplication {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application {
                setupAuth(
                    jwkProviderTokenX = jwkProvider,
                    tokenXIssuer = "tokenXissuer",
                    jwkProviderAadV2 = jwkProvider,
                    environment = getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }

            val response =
                client.get(sykmeldingerV2Uri) {
                    headers {
                        append(
                            HttpHeaders.Authorization,
                            "Bearer ${
                                generateJWT(
                                    "",
                                    "clientid",
                                    subject = "123",
                                    issuer = "microsoft",
                                )
                            }",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
    }
}
