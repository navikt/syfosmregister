package no.nav.syfo.sykmeldingstatus.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import java.nio.file.Paths
import java.time.LocalDateTime
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getVaultSecrets
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingSendApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)

    beforeEachTest {
        every { sykmeldingStatusKafkaProducer.send(any()) } just Runs
    }

    describe("Test SykmeldingSendAPI") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            application.routing { registerSykmeldingSendApi(sykmeldingStatusService, sykmeldingStatusKafkaProducer) }

            it("Skal returnere Created hvis alt går bra") {
                val sykmeldingId = "123"
                every { sykmeldingStatusService.registrerSendt(any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Returnerer 500 hvis noe går galt") {
                val sykmeldingId = "1235"
                every { sykmeldingStatusService.registrerSendt(any()) } throws RuntimeException("Noe gikk galt")
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                }
            }
        }
    }

    describe("Test SykmeldingSendAPI med tilgangskontroll") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }

            val env = Environment(clientId = "1",
                    appIds = listOf("10", "11"),
                    jwtIssuer = "issuer",
                    cluster = "cluster",
                    mountPathVault = "",
                    kafkaBootstrapServers = "",
                    syfosmregisterDBURL = "",
                    stsOidcIssuer = "https://security-token-service.nais.preprod.local",
                    stsOidcAudience = "preprod.local")

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            application.setupAuth(getVaultSecrets(), mockJwkProvider, "issuer1", env, mockJwkProvider, jwkProvider, jwkProvider)
            application.routing { authenticate("oidc") { registerSykmeldingSendApi(sykmeldingStatusService, sykmeldingStatusKafkaProducer) } }

            it("Should authenticate") {
                val sykmeldingId = "123"
                every { sykmeldingStatusService.registrerSendt(any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                            "preprod.local",
                            subject = "srvsyfoservice",
                            issuer = env.stsOidcIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Should not authenticate") {
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/123/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("Authorization", "Bearer ${generateJWT(
                            "client",
                            "preprod.local",
                            subject = "srvsyforegister",
                            issuer = env.stsOidcIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

fun opprettSykmeldingSendEventDTO(): SykmeldingSendEventDTO =
    SykmeldingSendEventDTO(
        LocalDateTime.now(),
        ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
        listOf(SporsmalOgSvarDTO("", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
            SporsmalOgSvarDTO("", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI"))
    )
