package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.getSykmeldingDto
import no.nav.syfo.testutil.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import java.nio.file.Paths
import java.time.LocalDate

class SykmeldingApiV2KtTest : FunSpec({

    val sykmeldingerV2Uri = "api/v2/sykmeldinger"

    val sykmeldingerService = mockkClass(SykmeldingerService::class)

    val mockPayload = mockk<Payload>()

    afterTest {
        clearAllMocks()
    }

    context("Test sykmeldingApiV2") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing {
                route("/api/v2") {
                    registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                }
            }

            test("Should get sykmeldinger for user with exclude filter") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?exclude=APEN") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Should get sykmeldinger for user with include filter") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?include=APEN") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Should get sykmeldinger for user with multiple exclude filters") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?exclude=APEN&exclude=SENDT") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }

            test("Should get bad request when exclude and include filters are in request") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?exclude=APEN&exclude=SENDT&include=AVBRUTT") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            test("Should get bad request when exclude filter is invalid") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?exclude=ÅPEN") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            test("Should get bad request when include filter is invalid") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?include=ALL") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            test("Should get sykmeldinger for user") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), null, null, any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }

            test("should get sykmeldinger for user with FOM and TOM queryparams") {
                val periode = getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 1, 20),
                    tom = LocalDate.of(2020, 2, 10)
                )
                coEvery {
                    sykmeldingerService.getUserSykmelding(
                        any(), periode.fom, periode.tom, any(), any(), any()
                    )
                } returns listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode)
                    )
                )
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?fom=2020-01-20&tom=2020-02-10") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.content!!)
                    sykmeldinger.size shouldBeEqualTo 1
                }
            }

            test("should not get sykmeldinger with only FOM") {
                val periode = getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 2, 11),
                    tom = LocalDate.of(2020, 2, 20)
                )
                coEvery { sykmeldingerService.getUserSykmelding(any(), LocalDate.of(2020, 2, 20), null, any(), any(), any()) } returns listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode)
                    )
                )
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?fom=2020-02-20") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.content!!)
                    sykmeldinger.size shouldBeEqualTo 1
                }
            }

            test("should not get sykmeldinger with only TOM") {
                val periode = getSykmeldingperiodeDto(
                    fom = LocalDate.of(2020, 2, 11),
                    tom = LocalDate.of(2020, 2, 20)
                )
                coEvery { sykmeldingerService.getUserSykmelding(any(), null, LocalDate.of(2020, 2, 20), any(), any(), any()) } returns listOf(
                    getSykmeldingDto(
                        perioder = listOf(periode)
                    )
                )
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?tom=2020-02-20") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldinger: List<SykmeldingDTO> = objectMapper.readValue(response.content!!)
                    sykmeldinger.size shouldBeEqualTo 1
                }
            }
            test("Skal få Bad Requeset om TOM dato er før FOM dato") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri?fom=2020-05-20&tom=2020-02-10") {
                        call.authentication.principal = BrukerPrincipal("123", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    response.content!! shouldBeEqualTo "\"FOM should be before or equal to TOM\""
                }
            }
        }
    }

    context("Test with autentication") {
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(
                loginserviceIdportenAudience = listOf("loginservice-client-id"),
                jwkProvider = jwkProvider,
                jwkProviderTokenX = jwkProvider,
                issuer = "https://sts.issuer.net/myid",
                tokenXIssuer = "",
                jwkProviderAadV2 = jwkProvider,
                environment = getEnvironment()
            )
            application.routing {
                route("/api/v2") {
                    authenticate("jwt") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }
            test("get sykmeldinger OK") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("", "loginservice-client-id", subject = "123")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }

            test("get sykmeldinger Unauthorized for nivå 3") {
                coEvery { sykmeldingerService.getUserSykmelding(any(), any(), any(), any(), any()) } returns listOf(getSykmeldingDto())
                with(
                    handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("", "loginservice-client-id", subject = "123", level = "Level3")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            test("Get sykmeldinger Unauthorized without JWT") {
                with(handleRequest(HttpMethod.Get, sykmeldingerV2Uri)) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            test("Get sykmeldinger Unauthorized with incorrect audience") {
                with(
                    handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                        addHeader("Authorization", "Bearer ${generateJWT("", "error", subject = "123")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            test("Get sykmeldinger Unauthorized with incorrect issuer") {
                with(
                    handleRequest(HttpMethod.Get, sykmeldingerV2Uri) {
                        addHeader("Authorization", "Bearer ${generateJWT("", "loginservice-client-id", subject = "123", issuer = "microsoft")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
