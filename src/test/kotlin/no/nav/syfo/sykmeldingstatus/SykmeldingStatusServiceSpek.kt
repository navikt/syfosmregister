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

    afterGroup {
        database.stop()
    }

    describe("Test registrerStatus") {
        beforeEachTest {
            database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
            database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        }

        afterEachTest {
            database.connection.dropData()
        }

        it("Should get bekreftetDato = null when not read by user") {
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldBe null
        }

        it("Should get bekreftdato = null when status is OPEN") {
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", LocalDateTime.now(), StatusEvent.APEN))
            savedSykmelding.bekreftetDato shouldBe null
        }

        it("Should get bekreftetDato") {
            val confirmedDateTime = LocalDateTime.now()
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should get bekreftetDato when newest status is SENDT") {
            val confirmedDateTime = LocalDateTime.now().minusDays(1)
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime.plusDays(1), StatusEvent.SENDT))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should throw error when inserting same status") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
            sykmeldingStatusService.registrerStatus(status)
            assertFailsWith(PSQLException::class) { sykmeldingStatusService.registrerStatus(status) }
        }

        it("Should not get sykmeling with status SLETTET") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.APEN)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger shouldEqual emptyList()
        }

        it("should only get sykmelidnger where status is not SLETTET") {
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
