package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class TimestampUtilTest : Spek({
    describe("Convert LocalDateTime to correct ZonedDateTime") {
        it("Should convert localDateTime to zonedDatetime") {
            val localDateTime = LocalDateTime.parse("2020-01-01T12:00:01.123")
            val zonedDateTime: ZonedDateTime = ZonedDateTime.parse("2020-01-01T11:00:01.123Z")
            val convertZonedDateTime = TimestampUtil.getAdjustedZonedDateTime(localDateTime)
            zonedDateTime shouldEqual convertZonedDateTime
        }
    }
})
