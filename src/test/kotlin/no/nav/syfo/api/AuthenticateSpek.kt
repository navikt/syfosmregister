package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import java.nio.file.Paths
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

class AuthenticateSpek :
    FunSpec(
        {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            val database = TestDB.database
            val sykmeldingerService = SykmeldingerService(database)

            beforeTest {
                database.connection.dropData()
                database.lagreMottattSykmelding(
                    testSykmeldingsopplysninger,
                    testSykmeldingsdokument,
                )
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
            }

            afterSpec { TestDB.stop() }

            context("Authenticate basicauth") {
                test("Aksepterer gyldig JWT med riktig audience") {
                    testApplication {
                        application {
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
                                            sykmeldingerService,
                                        )
                                    }
                                }
                            }
                            install(ContentNegotiation) {
                                jackson {
                                    registerKotlinModule()
                                    registerModule(JavaTimeModule())
                                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                }
                            }
                        }
                        val response =
                            client.get("api/v3/sykmeldinger") {
                                headers {
                                    append("Content-Type", "application/json")
                                    append(
                                        HttpHeaders.Authorization,
                                        "Bearer ${generateJWT("2", "clientid")}",
                                    )
                                }
                            }
                        response.status shouldBeEqualTo HttpStatusCode.OK
                    }
                }

                test("Gyldig JWT med feil audience gir Unauthorized") {
                    testApplication {
                        application {
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
                                            sykmeldingerService,
                                        )
                                    }
                                }
                            }
                            install(ContentNegotiation) {
                                jackson {
                                    registerKotlinModule()
                                    registerModule(JavaTimeModule())
                                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                }
                            }
                        }

                        val response =
                            client.get("api/v3/sykmeldinger") {
                                headers {
                                    append(
                                        HttpHeaders.Authorization,
                                        "Bearer ${generateJWT("2", "annenClientId")}",
                                    )
                                }
                            }
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                test("Gyldig JWT med feil issuer gir Unauthorized") {
                    testApplication {
                        application {
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
                                            sykmeldingerService,
                                        )
                                    }
                                }
                            }
                            install(ContentNegotiation) {
                                jackson {
                                    registerKotlinModule()
                                    registerModule(JavaTimeModule())
                                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                }
                            }
                        }
                        val response =
                            client.get("api/v3/sykmeldinger") {
                                headers {
                                    append(
                                        HttpHeaders.Authorization,
                                        "Bearer ${
                                            generateJWT(
                                                "2",
                                                "clientid",
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
        },
    )
