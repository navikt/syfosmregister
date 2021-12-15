package no.nav.syfo.testutil

import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun getNowTickMillisOffsetDateTime(): OffsetDateTime {
    return OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
}

fun getNowTickMillisLocalDateTime(): LocalDateTime {
    return LocalDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
}
