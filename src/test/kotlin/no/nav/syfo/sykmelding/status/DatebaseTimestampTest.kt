package no.nav.syfo.sykmelding.status

import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime

class DatebaseTimestampTest : Spek({

    val db = TestDB()

    afterEachTest {
        db.connection.dropData()
    }

    afterGroup {
        db.stop()
    }

    describe("Test db") {
        it("Should save timestamp as utc") {
            val timestamp = OffsetDateTime.parse("2019-06-02T12:00:01.123Z")
            val sykmeldingStatusEvent = SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN)
            db.lagreMottattSykmelding(
                testSykmeldingsopplysninger.copy(id = "123"),
                testSykmeldingsdokument.copy(id = "123")
            )
            db.registerStatus(sykmeldingStatusEvent)
            db.connection.opprettBehandlingsutfall(behandlingsutfall("123"))
            val statuser = db.hentSykmeldingStatuser("123")
            statuser.size shouldBeEqualTo 1
            statuser[0].timestamp shouldBeEqualTo timestamp
        }

        it("Should convert and save timestampZ if not provided") {
            val timestamp = OffsetDateTime.parse("2019-01-01T12:00:01.1234Z")
            val sykmeldingStatusEvent = SykmeldingStatusEvent("1234", timestamp, StatusEvent.APEN)
            db.lagreMottattSykmelding(
                testSykmeldingsopplysninger.copy(id = "1234"),
                testSykmeldingsdokument.copy(id = "1234")
            )
            db.registerStatus(sykmeldingStatusEvent)
            db.connection.opprettBehandlingsutfall(behandlingsutfall("1234"))
            val statuser = db.hentSykmeldingStatuser("1234")
            statuser.size shouldBeEqualTo 1
            statuser[0].timestamp shouldBeEqualTo timestamp
        }
    }
})

private fun behandlingsutfall(id: String) =
    Behandlingsutfall(
        id = id,
        behandlingsutfall = ValidationResult(Status.OK, emptyList())
    )
