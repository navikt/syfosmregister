package no.nav.syfo.rerunkafka.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.mockkClass
import java.nio.file.Paths
import java.util.UUID
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.rerunkafka.service.RerunKafkaService
import no.nav.syfo.testutil.generateJWT
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class RerunApiKtTest : Spek({

    val rerunKafkaService = mockkClass(RerunKafkaService::class)
    every { rerunKafkaService.rerun(any()) } returns emptyList()

    describe("Test api") {
        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    registerKotlinModule()
                }
            }
            application.routing { registerRerunKafkaApi(rerunKafkaService) }

            it("Should call rerunkafkaApi") {
                with(handleRequest(HttpMethod.Post, "/api/v1/rerun") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(objectMapper.writeValueAsString(RerunRequest(0.until(3).map { UUID.randomUUID().toString() }, ValidationResult(Status.OK, emptyList()))))
                }) {
                    response.status() shouldEqual HttpStatusCode.Accepted
                }
            }
        }
    }

    describe("Test API with security") {
        val path = "src/test/resources/jwkset.json"
        val uri = Paths.get(path).toUri().toURL()
        val jwkProvider = JwkProviderBuilder(uri).build()
        val env = Environment(clientId = "1",
                appIds = listOf("10", "11"),
                jwtIssuer = "https://sts.issuer.net/myid",
                cluster = "cluster",
                mountPathVault = "",
                kafkaBootstrapServers = "",
                syfosmregisterDBURL = "",
                stsOidcAudience = "",
                stsOidcIssuer = "")

        val vaultSecrets = VaultSecrets("", "", "", "", "", "", "")
        with(TestApplicationEngine()) {
            start()

            application.setupAuth(vaultSecrets, jwkProvider, "https://sts.issuer.net/myid", env, jwkProvider, jwkProvider)
            application.install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    registerKotlinModule()
                }
            }

            application.routing { authenticate("rerun") { registerRerunKafkaApi(rerunKafkaService) } }

            it("Should authenticate") {
                with(handleRequest(HttpMethod.Post, "api/v1/rerun") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("10", "1")}"
                    )
                    setBody(objectMapper.writeValueAsString(RerunRequest(0.until(3).map { UUID.randomUUID().toString() }, ValidationResult(Status.OK, emptyList()))))
                }) {
                    response.status() shouldEqual HttpStatusCode.Accepted
                }
            }

            it("Should not authenticate") {
                with(handleRequest(HttpMethod.Post, "api/v1/rerun") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("2", "1")}"
                    )
                    setBody(objectMapper.writeValueAsString(RerunRequest(0.until(3).map { UUID.randomUUID().toString() }, ValidationResult(Status.OK, emptyList()))))
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
