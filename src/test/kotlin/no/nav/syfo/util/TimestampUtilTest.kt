package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class TimestampUtilTest : FunSpec({
    test("Should return mottattTimestamp") {
        val mottattTimestamp = LocalDateTime.now().minusHours(1)
        val timestamp = TimestampUtil.getMinTime(mottattTimestamp, OffsetDateTime.of(mottattTimestamp, ZoneOffset.UTC))

        mottattTimestamp shouldBeEqualTo timestamp.toLocalDateTime()
    }

    test("Should return currentTimeStamp") {
        val mottattTimestamp = LocalDateTime.now().plusSeconds(1)
        val currentTimeStamp = OffsetDateTime.now(ZoneOffset.UTC)
        val timestamp = TimestampUtil.getMinTime(mottattTimestamp, currentTimeStamp)

        currentTimeStamp shouldBeEqualTo timestamp
    }
})
