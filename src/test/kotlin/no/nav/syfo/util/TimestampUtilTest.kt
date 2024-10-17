package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

internal class TimestampUtilTest {

    @Test
    internal fun `Should return mottattTimestamp`() {
        val mottattTimestamp = LocalDateTime.now().minusHours(1)
        val timestamp =
            TimestampUtil.getMinTime(
                mottattTimestamp,
                OffsetDateTime.of(mottattTimestamp, ZoneOffset.UTC),
            )

        mottattTimestamp shouldBeEqualTo timestamp.toLocalDateTime()
    }

    @Test
    internal fun `Should return currentTimeStamp`() {
        val mottattTimestamp = LocalDateTime.now().plusSeconds(1)
        val currentTimeStamp = OffsetDateTime.now(ZoneOffset.UTC)
        val timestamp = TimestampUtil.getMinTime(mottattTimestamp, currentTimeStamp)

        currentTimeStamp shouldBeEqualTo timestamp
    }
}
