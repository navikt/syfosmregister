package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.util.Base64
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object AuthenticateSpek : Spek({

    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()

    val database = TestDB()
    val sykmeldingerService = SykmeldingerService(database)

    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Authenticate basicauth") {
        with(TestApplicationEngine()) {
            start()
            application.setupAuth(
                    listOf("clientId"),
                    VaultSecrets(
                        serviceuserUsername = "username",
                        serviceuserPassword = "password",
                        syfomockUsername = "syfomock",
                        syfomockPassword = "test",
                        internalJwtIssuer = "",
                        internalJwtWellKnownUri = "",
                        internalLoginServiceClientId = ""
                    ),
                    jwkProvider,
                    "https://sts.issuer.net/myid",
                    jwkProvider,
                    "",
                    "",
                    emptyList(),
                    jwkProvider,
                    getEnvironment()
            )
            application.routing {
                authenticate("jwt") {
                    registrerSykmeldingApiV2(sykmeldingerService)
                }
                authenticate("basic") {
                    registerNullstillApi(database, "dev-fss")
                }
            }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            it("Validerer syfomock servicebruker") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/aktorId") {
                    addHeader(
                            HttpHeaders.Authorization,
                            "Basic ${Base64.getEncoder().encodeToString("syfomock:test".toByteArray())}"
                    )
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }

            it("Validerer ikke ugyldig servicebruker") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/aktorId") {
                    addHeader(
                            HttpHeaders.Authorization,
                            "Basic ${Base64.getEncoder().encodeToString("feil:passord".toByteArray())}"
                    ) // feil:passord
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Feiler om det mangler auth-header") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/aktorId")) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Aksepterer gyldig JWT med riktig audience") {
                with(handleRequest(HttpMethod.Get, "api/v2/sykmeldinger") {
                    addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "clientId")}"
                    )
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }

            it("Gyldig JWT med feil audience gir Unauthorized") {
                with(handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                    addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "annenClientId")}"
                    )
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Gyldig JWT med feil issuer gir Unauthorized") {
                with(handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                    addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "clientId", issuer = "microsoft")}"
                    )
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
