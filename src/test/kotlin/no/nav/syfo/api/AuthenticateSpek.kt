package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.ApplicationState
import no.nav.syfo.VaultSecrets
import no.nav.syfo.initRouting
import no.nav.syfo.testutil.TestDB
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
object AuthenticateSpek : Spek({

    val database = TestDB()
    val randomPort = ServerSocket(0).use { it.localPort }
    val fakeApi = embeddedServer(Netty, randomPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
        routing {
            fakeJWT()
        }
    }.start(wait = false)

    afterGroup {
        fakeApi.stop(0L, 0L, TimeUnit.SECONDS)
        database.stop()
    }

    describe("Authenticate basicauth") {
        with(TestApplicationEngine()) {
            start()

            application.initRouting(
                ApplicationState(), database, VaultSecrets(
                    serviceuserUsername = "username",
                    serviceuserPassword = "password",
                    oidcWellKnownUri = "http://localhost:$randomPort/fake.jwt",
                    loginserviceClientId = "clientId",
                    syfomockUsername = "syfomock",
                    syfomockPassword = "test"
                ),
                cluster = "dev-fss"
            )

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
        }
    }
})

@KtorExperimentalAPI
fun Route.fakeJWT() {
    get("fake.jwt") {
        call.respond(
            WellKnown(
                authorization_endpoint = "http://auth.url",
                token_endpoint = "http://token.url",
                jwks_uri = "https://jwks.url",
                issuer = "NAV"
            )
        )
    }
}
