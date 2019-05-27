package no.nav.syfo.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.aksessering.db.finnBrukersSykmeldinger
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.nullstilling.opprettSykmelding
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.persistering.PersistedSykmelding
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

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
                database.connection.setupTestData()
            }

            afterEachTest {
                database.connection.dropData()
            }

            it("Nullstiller bruker") {
                database.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status() shouldEqual HttpStatusCode.OK
                }

                database.finnBrukersSykmeldinger("pasientFnr").shouldBeEmpty()
            }

            it("Nullstiller ikke annen brukers sykmeldinger") {
                database.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/annenAktor")) {
                    response.status() shouldEqual HttpStatusCode.OK
                }

                database.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()
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

private fun Connection.setupTestData() {
    opprettSykmelding(
        PersistedSykmelding(
            id = "uuid",
            pasientFnr = "pasientFnr",
            pasientAktoerId = "pasientAktorId",
            legeFnr = "legeFnr",
            legeAktoerId = "legeAktorId",
            mottakId = "eid-1",
            legekontorOrgNr = "lege-orgnummer",
            legekontorHerId = "legekontorHerId",
            legekontorReshId = "legekontorReshId",
            epjSystemNavn = "epjSystemNavn",
            epjSystemVersjon = "epjSystemVersjon",
            mottattTidspunkt = LocalDateTime.now(),
            sykmelding = Sykmelding(
                andreTiltak = "andreTiltak",
                arbeidsgiver = Arbeidsgiver(
                    harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                    navn = "Arbeidsgiver AS",
                    yrkesbetegnelse = "aktiv",
                    stillingsprosent = 100
                ),
                avsenderSystem = AvsenderSystem(
                    navn = "avsenderSystem",
                    versjon = "versjon-1.0"
                ),
                behandler = Behandler(
                    fornavn = "Fornavn",
                    mellomnavn = "Mellomnavn",
                    aktoerId = "legeAktorId",
                    etternavn = "Etternavn",
                    adresse = Adresse(
                        gate = null,
                        postboks = null,
                        postnummer = null,
                        kommune = null,
                        land = null
                    ),
                    fnr = "legeFnr",
                    hpr = "hpr",
                    her = "her",
                    tlf = "tlf"
                ),
                behandletTidspunkt = LocalDateTime.now(),
                id = "id",
                kontaktMedPasient = KontaktMedPasient(
                    kontaktDato = null,
                    begrunnelseIkkeKontakt = null
                ),
                medisinskVurdering = MedisinskVurdering(
                    hovedDiagnose = Diagnose("2.16.578.1.12.4.1.1.7170", "Z01"),
                    biDiagnoser = emptyList(),
                    svangerskap = false,
                    yrkesskade = false,
                    yrkesskadeDato = null,
                    annenFraversArsak = null
                ),
                meldingTilArbeidsgiver = "",
                meldingTilNAV = null,
                msgId = "msgId",
                pasientAktoerId = "pasientAktoerId",
                perioder = listOf(
                    Periode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now(),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(
                            medisinskArsak = null,
                            arbeidsrelatertArsak = null
                        ),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false
                    )
                ),
                prognose = null,
                signaturDato = LocalDateTime.now(),
                skjermesForPasient = false,
                syketilfelleStartDato = LocalDate.now(),
                tiltakArbeidsplassen = "tiltakArbeidsplassen",
                tiltakNAV = "tiltakNAV",
                utdypendeOpplysninger = emptyMap()
            ),
            behandlingsUtfall = ValidationResult(Status.OK, emptyList()),
            tssid = "21455"
        )
    )
}
