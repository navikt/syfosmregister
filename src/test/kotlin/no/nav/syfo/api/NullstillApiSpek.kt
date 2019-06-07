package no.nav.syfo.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.persistering.opprettTomSykmeldingsmetadata
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
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
                database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
                database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
                database.connection.opprettTomSykmeldingsmetadata("uuid")
            }

            afterEachTest {
                database.connection.dropData()
            }

            it("Nullstiller bruker") {
                database.hentSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldEqual HttpStatusCode.OK
                }

                database.hentSykmeldinger("pasientFnr").shouldBeEmpty()
            }

            it("Nullstiller ikke annen brukers sykmeldinger") {
                database.hentSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/annenAktor")) {
                    response.status() shouldEqual HttpStatusCode.OK
                }

                database.hentSykmeldinger("pasientFnr").shouldNotBeEmpty()
            }

            it("Er tilgjengelig i test") {
                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldEqual HttpStatusCode.OK
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
                    response.status() shouldBe null
                }
            }
        }
    }
})
