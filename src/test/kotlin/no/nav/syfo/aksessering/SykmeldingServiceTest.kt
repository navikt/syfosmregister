package no.nav.syfo.aksessering

import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent
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
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", LocalDateTime.now(), StatusEvent.APEN))
            savedSykmelding.bekreftetDato shouldBe null
        }

        it("Should get bekreftetDato") {
            val confirmedDateTime = LocalDateTime.now()
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should get bekreftetDato when newest status is SENDT") {
            val confirmedDateTime = LocalDateTime.now().minusDays(1)
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            sykmeldingService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime.plusDays(1), StatusEvent.SENDT))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Should throw error when inserting same status") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
            sykmeldingService.registrerStatus(status)
            assertFailsWith(PSQLException::class) { sykmeldingService.registrerStatus(status) }
        }
    }
})
