package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.setupAuth
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
import org.amshove.kluent.shouldBeEqualTo
import java.nio.file.Paths

class AuthenticateSpek : FunSpec({

    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()
    val database = TestDB.database
    val sykmeldingerService = SykmeldingerService(database)

    beforeTest {
        database.connection.dropData()
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }

    afterSpec {
        TestDB.stop()
    }

    context("Authenticate basicauth") {
        with(TestApplicationEngine()) {
            start()
            application.setupAuth(
                listOf("clientId"),
                jwkProvider,
                jwkProvider,
                "https://sts.issuer.net/myid",
                "tokenXissuer",
                jwkProvider,
                getEnvironment()
            )
            application.routing {
                route("/api/v2") {
                    authenticate("jwt") {
                        registrerSykmeldingApiV2(sykmeldingerService)
                    }
                }
            }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            test("Aksepterer gyldig JWT med riktig audience") {
                with(
                    handleRequest(HttpMethod.Get, "api/v2/sykmeldinger") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "clientId")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }

            test("Gyldig JWT med feil audience gir Unauthorized") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "annenClientId")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            test("Gyldig JWT med feil issuer gir Unauthorized") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "clientId", issuer = "microsoft")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
