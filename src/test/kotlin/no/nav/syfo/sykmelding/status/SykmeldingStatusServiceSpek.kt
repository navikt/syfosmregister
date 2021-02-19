package no.nav.syfo.sykmelding.status

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.aksessering.db.hentSporsmalOgSvar
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusServiceSpek : Spek({

    val database = TestDB()
    val sykmeldingService = SykmeldingService(database)
    val sykmeldingStatusService = SykmeldingStatusService(database)

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

    describe("Test registrerStatus") {
        it("BekreftetDato skal være null når sykmelding ikke er bekreftet") {
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldBe null
            savedSykmelding.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.APEN
        }

        it("Skal få bekreftetDato hvis sykmelding er bekreftet") {
            val confirmedDateTime = OffsetDateTime.now(ZoneOffset.UTC)
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
            savedSykmelding.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
        }

        it("Skal ikke kaste feil hvis man oppdaterer med eksisterende status på nytt") {
            val confirmedDateTime = OffsetDateTime.now(ZoneOffset.UTC)
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(status)

            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Skal ikke hente sykmeldinger med status SLETTET") {
            val confirmedDateTime = OffsetDateTime.now(ZoneOffset.UTC)
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.APEN)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            sykmeldingStatusService.registrerStatus(status)
            database.registerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger shouldEqual emptyList()
        }

        it("Skal kun hente sykmeldinger der status ikke er SLETTET") {
            val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
            val copySykmeldingopplysning = testSykmeldingsopplysninger.copy(
                    id = "uuid2",
                    pasientFnr = "pasientFnr"
            )
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(SykmeldingStatusEvent(copySykmeldingopplysning.id, copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

            val confirmedDateTime = OffsetDateTime.now(ZoneOffset.UTC)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            database.registerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger.size shouldEqual 1
            sykmeldinger.first().id shouldEqual "uuid2"
        }

        it("Skal hente alle statuser") {
            database.registerStatus(SykmeldingStatusEvent("uuid", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10), StatusEvent.SENDT))
            val sykmeldingStatuser = sykmeldingStatusService.getSykmeldingStatus("uuid", null)
            sykmeldingStatuser.size shouldEqual 2
        }

        it("Skal hente siste status") {
            database.registerStatus(SykmeldingStatusEvent("uuid", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(10), StatusEvent.SENDT))
            val sykmeldingstatuser = sykmeldingStatusService.getSykmeldingStatus("uuid", "LATEST")
            sykmeldingstatuser.size shouldEqual 1
            sykmeldingstatuser[0].event shouldEqual StatusEvent.SENDT
            sykmeldingstatuser[0].erAvvist shouldEqual false
            sykmeldingstatuser[0].erEgenmeldt shouldEqual false
        }

        it("Status skal vises som avvist hvis sykmelding er avvist") {
            val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
            val copySykmeldingopplysning = testSykmeldingsopplysninger.copy(
                id = "uuid2"
            )
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(SykmeldingStatusEvent(copySykmeldingopplysning.id, copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
            database.connection.opprettBehandlingsutfall(Behandlingsutfall(id = "uuid2", behandlingsutfall = ValidationResult(Status.INVALID, listOf(RuleInfo("navn", "message", "message", Status.INVALID)))))

            val sykmeldingstatuser = sykmeldingStatusService.getSykmeldingStatus("uuid2", "LATEST")

            sykmeldingstatuser[0].erAvvist shouldEqual true
        }

        it("Status skal vises som egenmeldt hvis sykmelding er egenmelding") {
            val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
            val copySykmeldingopplysning = testSykmeldingsopplysninger.copy(
                id = "uuid2", epjSystemNavn = "Egenmeldt"
            )
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(SykmeldingStatusEvent(copySykmeldingopplysning.id, copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

            val sykmeldingstatuser = sykmeldingStatusService.getSykmeldingStatus("uuid2", "LATEST")

            sykmeldingstatuser[0].erEgenmeldt shouldEqual true
        }

        it("registrer bekreftet skal ikke lagre spørsmål og svar om den ikke er nyest") {
            val sporsmal = listOf(Sporsmal("tekst", ShortName.FORSIKRING, Svar("uuid", 1, Svartype.JA_NEI, "NEI")))
            sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEvent("uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC).minusSeconds(1),
                    sporsmal
            ))
            val savedSporsmals = database.connection.hentSporsmalOgSvar("uuid")
            savedSporsmals.size shouldEqual 0
        }

        it("registrer bekreft skal lagre spørsmål") {
            val sporsmal = listOf(Sporsmal("tekst", ShortName.FORSIKRING, Svar("uuid", 1, Svartype.JA_NEI, "NEI")))
            sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEvent("uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC).plusSeconds(1),
                    sporsmal
            ))
            val savedSporsmals = database.connection.hentSporsmalOgSvar("uuid")
            savedSporsmals shouldEqual sporsmal
        }

        it("registrer APEN etter BEKREFTET skal slette sporsmal og svar") {
            val sporsmal = listOf(Sporsmal("tekst", ShortName.FORSIKRING, Svar("uuid", 1, Svartype.JA_NEI, "NEI")))
            sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEvent("uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC).plusSeconds(1),
                    sporsmal
            ))
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid",
                    testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC).plusSeconds(2),
                    StatusEvent.APEN))
            val savedSporsmal2 = database.connection.hentSporsmalOgSvar("uuid")
            savedSporsmal2.size shouldEqual 0
        }

        it("Skal kunne hente SendtSykmeldingUtenDiagnose selv om behandlingsutfall mangler") {
            database.connection.dropData()
            database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
            database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
            database.registrerSendt(
                SykmeldingSendEvent(
                    testSykmeldingsopplysninger.id,
                    testSykmeldingsopplysninger.mottattTidspunkt.plusMinutes(5).atOffset(ZoneOffset.UTC),
                    ArbeidsgiverStatus(testSykmeldingsopplysninger.id, "orgnummer", null, "Bedrift"),
                    Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON,
                        Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))),
                SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.plusMinutes(5).atOffset(ZoneOffset.UTC), StatusEvent.SENDT)
            )
            val sendtSykmelding = sykmeldingStatusService.getEnkelSykmelding(testSykmeldingsopplysninger.id)
            sendtSykmelding shouldNotEqual null
        }
    }
})
