package no.nav.syfo.sykmelding.serviceuser.api

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockkClass
import java.nio.file.Paths
import java.time.ZoneOffset
import no.nav.syfo.application.setupAuth
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.registerStatus
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.getSykmeldingDto
import no.nav.syfo.testutil.getVaultSecrets
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingServiceuserApiTest : Spek({
    val sykmeldingUri = "api/v1/sykmelding/"

    val database = TestDB()
    val sykmeldingerService = SykmeldingerService(database)

    val sykmeldingerServiceMedMock = mockkClass(SykmeldingerService::class)

    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt, StatusEvent.APEN, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC)))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }
    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("Test sykmeldingServiceuserApi") {

        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registrerSykmeldingServiceuserApiV1(sykmeldingerService = sykmeldingerService) }
            it("Skal få sykmelding") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingUri/uuid") {
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
        }
    }

    describe("Test with autentication") {
        with(TestApplicationEngine()) {
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            setUpTestApplication()
            application.setupAuth(getVaultSecrets(), jwkProvider, "", jwkProvider, jwkProvider, "https://sts.issuer.net/myid", "clientId", listOf("syfosoknad"))
            application.routing { authenticate("jwtserviceuser") { registrerSykmeldingServiceuserApiV1(sykmeldingerService = sykmeldingerServiceMedMock) } }
            it("get sykmelding OK") {
                every { sykmeldingerServiceMedMock.getSykmeldingMedId(any()) } returns getSykmeldingDto(skjermet = true)
                with(handleRequest(HttpMethod.Get, "$sykmeldingUri/1234") {
                    addHeader(HttpHeaders.Authorization,
                        "Bearer ${generateJWT("syfosoknad", "clientId", subject = "123")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
            it("Get sykmelding Unauthorized without JWT") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingUri/1234")) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Get sykmelding Unauthorized with incorrect audience") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingUri/1234") {
                    addHeader("Authorization", "Bearer ${generateJWT("syfosoknad", "error", subject = "123")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }

            it("Get sykmelding Unauthorized with incorrect azp") {
                with(handleRequest(HttpMethod.Get, "$sykmeldingUri/1234") {
                    addHeader("Authorization", "Bearer ${generateJWT("error", "clientId", subject = "123")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
