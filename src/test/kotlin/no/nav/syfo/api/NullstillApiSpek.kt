package no.nav.syfo.api

import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.finnBrukersSykmeldinger
import no.nav.syfo.db.opprettSykmelding
import no.nav.syfo.model.*
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
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

    afterGroup {
        TestDB.cleanUp()
    }

    describe("NullstillApi") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerNullstillApi(TestDB)
            }

            beforeEachTest {
                TestDB.connection.setupTestData()
            }

            afterEachTest {
                TestDB.connection.dropData()
            }

            it("Nullstiller bruker") {
                TestDB.connection.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/pasientAktorId")) {
                    response.status()?.isSuccess() shouldEqual true
                }

                TestDB.connection.finnBrukersSykmeldinger("pasientFnr").shouldBeEmpty()
            }

            it("Nullstiller ikke annen brukers sykmeldinger") {
                TestDB.connection.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()

                with(handleRequest(HttpMethod.Delete, "/internal/nullstillSykmeldinger/annenAktor")) {
                    response.status()?.isSuccess() shouldEqual true
                }

                TestDB.connection.finnBrukersSykmeldinger("pasientFnr").shouldNotBeEmpty()
            }
        }
    }
})

private fun Connection.setupTestData() {
    opprettSykmelding(PersistedSykmelding(
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
                            hovedDiagnose = null,
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
                    perioder = listOf(Periode(
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
                    )),
                    prognose = null,
                    signaturDato = LocalDateTime.now(),
                    skjermesForPasient = false,
                    syketilfelleStartDato = LocalDate.now(),
                    tiltakArbeidsplassen = "tiltakArbeidsplassen",
                    tiltakNAV = "tiltakNAV",
                    utdypendeOpplysninger = emptyMap()
            ),
            behandlingsUtfall = ValidationResult(Status.OK, emptyList())
    ))
}
