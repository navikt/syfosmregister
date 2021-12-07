package no.nav.syfo.sykmelding.user.api

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.setupAuth
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getEnvironment
import no.nav.syfo.testutil.getVaultSecrets
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import java.time.ZoneOffset

class SykmeldingApiV2IntegrationTest : Spek({
    val sykmeldingerV2Uri = "api/v2/sykmeldinger"

    val database = TestDB()
    val sykmeldingerService = SykmeldingerService(database)

    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }
    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("SykmeldingApiV2 integration test") {
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(
                listOf("clientId"),
                getVaultSecrets(),
                jwkProvider,
                "",
                jwkProvider,
                getEnvironment()
            )
            application.routing { authenticate("jwt") { registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService) } }

            it("Skal få unauthorized når credentials mangler") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {}) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("Henter sykmelding når fnr stemmer med sykmeldingen") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientId", subject = "pasientFnr")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("Får NotFound med feil fnr, hvor sykmelding finnes i db") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/uuid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientId", subject = "feilFnr")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }

            it("Får NotFound med id som ikke finnes i databasen") {
                with(
                    handleRequest(HttpMethod.Get, "$sykmeldingerV2Uri/annenId") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT("syfosoknad", "clientId", subject = "pasientFnr")}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
