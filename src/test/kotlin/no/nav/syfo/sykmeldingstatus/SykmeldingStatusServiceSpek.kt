package no.nav.syfo.sykmeldingstatus

import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.postgresql.util.PSQLException
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusServiceSpek : Spek({

    val database = TestDB()
    val sykmeldingService = SykmeldingService(database)
    val sykmeldingStatusService = SykmeldingStatusService(database)

    beforeEachTest {
        database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
        database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, LocalDateTime.now(), StatusEvent.APEN))
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
            val confirmedDateTime = LocalDateTime.now()
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
            savedSykmelding.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
        }

        it("Skal kaste feil hvis man oppdaterer med eksisterende status på nytt") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
            sykmeldingStatusService.registrerStatus(status)
            assertFailsWith(PSQLException::class) { sykmeldingStatusService.registrerStatus(status) }
        }

        it("Skal ikke hente sykmeldinger med status SLETTET") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.APEN)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger shouldEqual emptyList()
        }

        it("Skal kun hente sykmeldinger der status ikke er SLETTET") {
            val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
            val copySkymeldingopplysning = testSykmeldingsopplysninger.copy(
                    id = "uuid2",
                    pasientFnr = "pasientFnr"
            )
            database.connection.opprettSykmeldingsopplysninger(copySkymeldingopplysning)
            database.connection.opprettSykmeldingsdokument(copySykmeldingDokument)
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.APEN)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            val status2 = SykmeldingStatusEvent("uuid2", confirmedDateTime, StatusEvent.APEN)
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(status2)
            sykmeldingStatusService.registrerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger.size shouldEqual 1
            sykmeldinger.first().id shouldEqual "uuid2"
        }
    }
})
