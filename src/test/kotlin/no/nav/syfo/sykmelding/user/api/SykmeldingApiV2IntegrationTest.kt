package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.testing.*
import java.nio.file.Paths
import java.time.ZoneOffset
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

class SykmeldingApiV2IntegrationTest :
    FunSpec(
        {
            val sykmeldingerV2Uri = "api/v3/sykmeldinger"

            val database = TestDB.database
            val sykmeldingerService = SykmeldingerService(database)

            beforeTest {
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
            }

            afterSpec { TestDB.stop() }

            context("SykmeldingApiV2 integration test") {
                test("Skal få unauthorized når credentials mangler") {
                    testApplication {
                        val path = "src/test/resources/jwkset.json"
                        val uri = Paths.get(path).toUri().toURL()
                        val jwkProvider = JwkProviderBuilder(uri).build()
                        setUpTestApplication()
                        application {
                            setupAuth(
                                jwkProvider,
                                "tokenXissuer",
                                jwkProvider,
                                getEnvironment(),
                            )
                        }
                        database.opprettBehandlingsutfall(testBehandlingsutfall)
                        routing {
                            route("/api/v3") {
                                authenticate("tokenx") {
                                    registrerSykmeldingApiV2(
                                        sykmeldingerService = sykmeldingerService
                                    )
                                }
                            }
                        }

                        val response = client.get("$sykmeldingerV2Uri/uuid")
                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                test("Henter sykmelding når fnr stemmer med sykmeldingen") {
                    testApplication {
                        val path = "src/test/resources/jwkset.json"
                        val uri = Paths.get(path).toUri().toURL()
                        val jwkProvider = JwkProviderBuilder(uri).build()
                        setUpTestApplication()
                        application {
                            setupAuth(
                                jwkProvider,
                                "tokenXissuer",
                                jwkProvider,
                                getEnvironment(),
                            )
                        }
                        routing {
                            route("/api/v3") {
                                authenticate("tokenx") {
                                    registrerSykmeldingApiV2(
                                        sykmeldingerService = sykmeldingerService
                                    )
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

                test("Får NotFound med feil fnr, hvor sykmelding finnes i db") {
                    testApplication {
                        val path = "src/test/resources/jwkset.json"
                        val uri = Paths.get(path).toUri().toURL()
                        val jwkProvider = JwkProviderBuilder(uri).build()
                        setUpTestApplication()
                        application {
                            setupAuth(
                                jwkProvider,
                                "tokenXissuer",
                                jwkProvider,
                                getEnvironment(),
                            )
                        }
                        routing {
                            route("/api/v3") {
                                authenticate("tokenx") {
                                    registrerSykmeldingApiV2(
                                        sykmeldingerService = sykmeldingerService
                                    )
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

                test("Får NotFound med id som ikke finnes i databasen") {
                    testApplication {
                        val path = "src/test/resources/jwkset.json"
                        val uri = Paths.get(path).toUri().toURL()
                        val jwkProvider = JwkProviderBuilder(uri).build()
                        setUpTestApplication()
                        application {
                            setupAuth(
                                jwkProvider,
                                "tokenXissuer",
                                jwkProvider,
                                getEnvironment(),
                            )
                        }
                        routing {
                            route("/api/v3") {
                                authenticate("tokenx") {
                                    registrerSykmeldingApiV2(
                                        sykmeldingerService = sykmeldingerService
                                    )
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

                test("Skal hente utenlandsk sykmelding") {
                    testApplication {
                        val path = "src/test/resources/jwkset.json"
                        val uri = Paths.get(path).toUri().toURL()
                        val jwkProvider = JwkProviderBuilder(uri).build()
                        setUpTestApplication()
                        application {
                            setupAuth(
                                jwkProvider,
                                "tokenXissuer",
                                jwkProvider,
                                getEnvironment(),
                            )
                        }
                        routing {
                            route("/api/v3") {
                                authenticate("tokenx") {
                                    registrerSykmeldingApiV2(
                                        sykmeldingerService = sykmeldingerService
                                    )
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
                        sykmelding.utenlandskSykmelding shouldBeEqualTo
                            UtenlandskSykmeldingDTO("Utland")
                    }
                }
            }
        },
    )
