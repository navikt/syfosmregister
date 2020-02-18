package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimestampUtil private constructor() {
    companion object {
        fun getAdjustedZonedDateTime(localDateTime: LocalDateTime): ZonedDateTime {
            return localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
        }
    }
}
