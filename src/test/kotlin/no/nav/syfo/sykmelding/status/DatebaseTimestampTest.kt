package no.nav.syfo.sykmelding.status

import java.time.OffsetDateTime
import no.nav.syfo.testutil.TestDB
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DatebaseTimestampTest : Spek({

    val db = TestDB()

    describe("Test db") {
        it("Should save timestamp as utc") {
            val timestamp = OffsetDateTime.parse("2019-06-02T12:00:01.123Z")
            val sykmeldingStatusEvent = SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN)
            db.registerStatus(sykmeldingStatusEvent)
            val statuser = db.hentSykmeldingStatuser("123")
            statuser.size shouldEqual 1
            statuser.get(0).timestamp shouldEqual timestamp
        }

        it("Should convert and save timestampZ if not provided") {
            val timestamp = OffsetDateTime.parse("2019-01-01T12:00:01.1234Z")
            val sykmeldingStatusEvent = SykmeldingStatusEvent("1234", timestamp, StatusEvent.APEN)
            db.registerStatus(sykmeldingStatusEvent)
            val statuser = db.hentSykmeldingStatuser("1234")
            statuser.size shouldEqual 1
            statuser.get(0).timestamp shouldEqual timestamp
        }
    }
})