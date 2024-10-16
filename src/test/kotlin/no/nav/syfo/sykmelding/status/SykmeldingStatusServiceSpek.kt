package no.nav.syfo.sykmelding.status

import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.createKomplettInnsendtSkjemaSvar
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SykmeldingStatusServiceSpek {
    val database = TestDB.database
    val sykmeldingerService = SykmeldingerService(database)
    val sykmeldingStatusService = SykmeldingStatusService(database)

    @BeforeEach
    fun beforeTest() {
        database.connection.dropData()
        runBlocking {
            database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
            database.registerStatus(
                SykmeldingStatusEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                    StatusEvent.APEN,
                ),
            )
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        }
    }

    @AfterEach
    fun afterTest() {
        database.connection.dropData()
    }

    companion object {
        @AfterAll
        @JvmStatic
        internal fun tearDown() {
            TestDB.stop()
        }
    }

    @Test
    internal fun `Test registrerStatus Skal ikke kaste feil hvis man oppdaterer med eksisterende status på nytt`() {

        val confirmedDateTime = getNowTickMillisOffsetDateTime().plusMonths(1)
        val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
        runBlocking {
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(status)

            val savedSykmelding = sykmeldingerService.getUserSykmelding("pasientFnr", null, null)[0]
            savedSykmelding.sykmeldingStatus.timestamp shouldBeEqualTo confirmedDateTime
        }
    }

    @Test
    internal fun `Test registrerStatus Skal hente siste status`() {
        runBlocking {
            database.registerStatus(
                SykmeldingStatusEvent(
                    "uuid",
                    getNowTickMillisOffsetDateTime().plusMonths(1).plusSeconds(10),
                    StatusEvent.SENDT,
                ),
            )
            val sykmeldingstatuser = sykmeldingStatusService.getLatestSykmeldingStatus("uuid")
            sykmeldingstatuser shouldNotBe null
            sykmeldingstatuser?.event shouldBeEqualTo StatusEvent.SENDT
            sykmeldingstatuser?.erAvvist shouldBeEqualTo false
            sykmeldingstatuser?.erEgenmeldt shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test registrerStatus Status skal vises som avvist hvis sykmelding er avvist`() {

        val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
        val copySykmeldingopplysning =
            testSykmeldingsopplysninger.copy(
                id = "uuid2",
            )
        runBlocking {
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(
                SykmeldingStatusEvent(
                    copySykmeldingopplysning.id,
                    copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                    StatusEvent.APEN,
                ),
            )
            database.connection.opprettBehandlingsutfall(
                Behandlingsutfall(
                    id = "uuid2",
                    behandlingsutfall =
                        ValidationResult(
                            Status.INVALID,
                            listOf(RuleInfo("navn", "message", "message", Status.INVALID)),
                        ),
                ),
            )

            val sykmeldingstatuser = sykmeldingStatusService.getLatestSykmeldingStatus("uuid2")

            sykmeldingstatuser?.erAvvist shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Test registrerStatus Status skal vises som egenmeldt hvis sykmelding er egenmelding`() {
        val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
        val copySykmeldingopplysning =
            testSykmeldingsopplysninger.copy(
                id = "uuid2",
                epjSystemNavn = "Egenmeldt",
            )
        runBlocking {
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(
                SykmeldingStatusEvent(
                    copySykmeldingopplysning.id,
                    copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                    StatusEvent.APEN,
                ),
            )
            database.connection.opprettBehandlingsutfall(
                testBehandlingsutfall.copy(id = "uuid2"),
            )

            val sykmeldingstatuser = sykmeldingStatusService.getLatestSykmeldingStatus("uuid2")

            sykmeldingstatuser?.erEgenmeldt shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Test registrerStatus registrer bekreftet skal ikke lagre spørsmål og svar om den ikke er nyest`() {

        val sporsmal =
            listOf(
                Sporsmal(
                    "tekst",
                    ShortName.FORSIKRING,
                    Svar("uuid", 1, Svartype.JA_NEI, "NEI"),
                ),
            )
        runBlocking {
            sykmeldingStatusService.registrerBekreftet(
                SykmeldingBekreftEvent(
                    "uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .atOffset(ZoneOffset.UTC)
                        .minusSeconds(1),
                    sporsmal,
                    brukerSvar = createKomplettInnsendtSkjemaSvar(),
                ),
                tidligereArbeidsgiver = null,
            )
            val savedSporsmals = database.connection.use { it.hentSporsmalOgSvar("uuid") }
            val alleSpm = database.getAlleSpm("uuid")
            savedSporsmals.size shouldBeEqualTo 0
            alleSpm shouldBeEqualTo null
        }
    }

    @Test
    internal fun `Test registrerStatus registrer bekreft skal lagre spørsmål`() {
        val sporsmal =
            listOf(
                Sporsmal(
                    "tekst",
                    ShortName.FORSIKRING,
                    Svar("uuid", 1, Svartype.JA_NEI, "NEI"),
                ),
            )
        runBlocking {
            sykmeldingStatusService.registrerBekreftet(
                SykmeldingBekreftEvent(
                    "uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .atOffset(ZoneOffset.UTC)
                        .plusSeconds(1),
                    sporsmal,
                    brukerSvar = createKomplettInnsendtSkjemaSvar(),
                ),
                tidligereArbeidsgiver = null,
            )
            val savedSporsmals = database.hentSporsmalOgSvar("uuid")
            val alleSpm = database.getAlleSpm("uuid")
            savedSporsmals shouldBeEqualTo sporsmal
            alleSpm shouldBeEqualTo createKomplettInnsendtSkjemaSvar()
        }
    }

    @Test
    internal fun `Test registrerStatus registrer APEN etter BEKREFTET skal slette sporsmal og svar`() {
        val sporsmal =
            listOf(
                Sporsmal(
                    "tekst",
                    ShortName.FORSIKRING,
                    Svar("uuid", 1, Svartype.JA_NEI, "NEI"),
                ),
            )
        runBlocking {
            sykmeldingStatusService.registrerBekreftet(
                SykmeldingBekreftEvent(
                    "uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .atOffset(ZoneOffset.UTC)
                        .plusSeconds(1),
                    sporsmal,
                    brukerSvar = null,
                ),
                tidligereArbeidsgiver = null,
            )
            sykmeldingStatusService.registrerStatus(
                SykmeldingStatusEvent(
                    "uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .atOffset(ZoneOffset.UTC)
                        .plusSeconds(2),
                    StatusEvent.APEN,
                ),
            )
            val savedSporsmal2 = database.connection.use { it.hentSporsmalOgSvar("uuid") }
            savedSporsmal2.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Test registrerStatus registrer Skal kunne hente SendtSykmeldingUtenDiagnose selv om behandlingsutfall mangler`() {
        database.connection.dropData()
        runBlocking {
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
            database.registrerSendt(
                SykmeldingSendEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .plusMinutes(5)
                        .atOffset(ZoneOffset.UTC),
                    ArbeidsgiverStatus(
                        testSykmeldingsopplysninger.id,
                        "orgnummer",
                        null,
                        "Bedrift",
                    ),
                    listOf(
                        Sporsmal(
                            "Arbeidssituasjon",
                            ShortName.ARBEIDSSITUASJON,
                            Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
                        ),
                    ),
                    brukerSvar = createKomplettInnsendtSkjemaSvar(),
                ),
                SykmeldingStatusEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .plusMinutes(5)
                        .atOffset(ZoneOffset.UTC),
                    StatusEvent.SENDT,
                ),
            )
            val sendtSykmelding =
                sykmeldingStatusService.getArbeidsgiverSykmelding(
                    testSykmeldingsopplysninger.id,
                )
            sendtSykmelding shouldNotBeEqualTo null
        }
    }

    @Test
    internal fun `Test registrerStatus registrer registrer sendt skal lagre alle spørsmål`() {
        val sporsmal =
            listOf(
                Sporsmal(
                    "Arbeidssituasjon",
                    ShortName.ARBEIDSSITUASJON,
                    Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
                ),
                Sporsmal(
                    "Er det Din Leder som skal følge deg opp mens du er syk?",
                    ShortName.NY_NARMESTE_LEDER,
                    Svar("uuid", 2, Svartype.JA_NEI, "JA"),
                ),
            )

        runBlocking {
            sykmeldingStatusService.registrerSendt(
                SykmeldingSendEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .plusMinutes(5)
                        .atOffset(ZoneOffset.UTC),
                    ArbeidsgiverStatus(
                        testSykmeldingsopplysninger.id,
                        "orgnummer",
                        null,
                        "Bedrift",
                    ),
                    sporsmal,
                    brukerSvar = createKomplettInnsendtSkjemaSvar(),
                ),
                SykmeldingStatusEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt
                        .plusMinutes(5)
                        .atOffset(ZoneOffset.UTC),
                    StatusEvent.SENDT,
                ),
            )

            val savedSporsmals = database.connection.use { it.hentSporsmalOgSvar("uuid") }
            savedSporsmals shouldBeEqualTo sporsmal
        }
    }
}
