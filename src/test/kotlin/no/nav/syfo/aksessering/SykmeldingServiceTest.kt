package no.nav.syfo.aksessering

import no.nav.syfo.aksessering.db.registerStatus
import no.nav.syfo.aksessering.db.registrerLestAvBruker
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent
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
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.postgresql.util.PSQLException
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import kotlin.test.assertFailsWith

class SykmeldingServiceTest : Spek({

    val database = TestDB()
    val sykmeldingService = SykmeldingService(database)

    afterGroup {
        database.stop()
    }

    describe("Test SykmeldingStatus") {

        val createdDateTime = LocalDateTime.now()
        beforeEachTest {
            database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
            database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
            database.registerStatus(SykmeldingStatusEvent("uuid", createdDateTime, StatusEvent.OPEN))
        }

        afterEachTest {
            database.connection.dropData()
        }

        it("Should get bekreftdato when sykmeling is confirmed in old table") {
            database.connection.opprettTomSykmeldingsmetadata("uuid")
            database.registrerLestAvBruker("uuid")
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldNotBe null
        }

        it("Should get bekreftetDato = null when not read by user") {
            database.connection.opprettTomSykmeldingsmetadata("uuid")
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldBe null
        }

        it("Should get bekreftdato = null when status is OPEN") {
            database.registrerLestAvBruker("uuid")
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldBe null
        }

        it("Should get bekreftetDato from new table") {
            val confirmedDateTime = LocalDateTime.now()
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.CONFIRMED))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should get bekreftetDato from new table when newest status is SENDT") {
            val confirmedDateTime = LocalDateTime.now().minusDays(1)
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.CONFIRMED))
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime.plusDays(1), StatusEvent.SENT))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should throw error when inserting same status") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.CONFIRMED)
            sykmeldingService.registrerStatus(status)
            assertFailsWith(PSQLException::class) { sykmeldingService.registrerStatus(status) }

        }
    }
})
