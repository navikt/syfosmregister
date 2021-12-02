package no.nav.syfo.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset

object NullstillApiSpek : Spek({

    val database = TestDB()

    afterGroup {
        database.stop()
    }

    describe("NullstillApi - testkontekst") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNullstillApi(database, "dev-fss")
            }

            beforeEachTest {
                database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
                database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
            }

            afterEachTest {
                database.connection.dropData()
            }

            it("Nullstiller bruker") {
                database.getSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }

                database.getSykmeldinger("pasientFnr").shouldBeEmpty()
            }

            it("Nullstiller ikke annen brukers sykmeldinger") {
                database.getSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/annenAktor")) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }

                database.getSykmeldinger("pasientFnr").shouldNotBeEmpty()
            }

            it("Er tilgjengelig i test") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
        }
    }

    describe("NullstillApi - prodkontekst") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNullstillApi(database, "prod-fss")
            }

            it("Er ikke tilgjengelig i prod") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
})
