package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
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
import java.nio.file.Paths
import java.time.ZoneOffset

class SykmeldingApiV2IntegrationTest : FunSpec({
    val sykmeldingerV2Uri = "api/v3/sykmeldinger"

    val database = TestDB.database
    val sykmeldingerService = SykmeldingerService(database)

    beforeTest {
        database.connection.dropData()
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }
    afterSpec {
        TestDB.stop()
    }

    context("SykmeldingApiV2 integration test") {
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(
                jwkProvider,
                "tokenXissuer",
                jwkProvider,
                getEnvironment(),
            )
            application.routing {
                route("/api/v3") {
                    authenticate("tokenx") {
                        registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                    }
                }
            }

            test("Skal få unauthorized når credentials mangler") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {}) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            test("Henter sykmelding når fnr stemmer med sykmeldingen") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientid", subject = "pasientFnr")}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmelding = objectMapper.readValue(response.content, SykmeldingDTO::class.java)
                    sykmelding.utenlandskSykmelding shouldBeEqualTo null
                }
            }

            test("Får NotFound med feil fnr, hvor sykmelding finnes i db") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientid", subject = "feilFnr")}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            test("Får NotFound med id som ikke finnes i databasen") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/annenId") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientid", subject = "pasientFnr")}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            test("Skal hente utenlandsk sykmelding") {
                database.updateMottattSykmelding(testSykmeldingsopplysninger.copy(utenlandskSykmelding = UtenlandskSykmelding("Utland", false)), testSykmeldingsdokument)
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientid", subject = "pasientFnr")}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmelding = objectMapper.readValue(response.content, SykmeldingDTO::class.java)
                    sykmelding.utenlandskSykmelding shouldBeEqualTo UtenlandskSykmeldingDTO("Utland")
                }
            }
        }
    }
})
