package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimestampUtil private constructor() {
    companion object {
        fun getAdjustedOffsetDateTime(localDateTime: LocalDateTime): OffsetDateTime {
            return localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }

        fun getAdjustedToLocalDateTime(timestamp: OffsetDateTime) =
                timestamp.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }
}
