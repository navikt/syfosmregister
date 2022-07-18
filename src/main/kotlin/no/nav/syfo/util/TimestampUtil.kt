package no.nav.syfo.util

import no.nav.syfo.log
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimestampUtil private constructor() {
    companion object {
        fun getAdjustedOffsetDateTime(localDateTime: LocalDateTime): OffsetDateTime {
            return localDateTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }

        fun getMinTime(mottattDato: LocalDateTime, currentTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): OffsetDateTime {
            val sykmeldingTime = OffsetDateTime.of(mottattDato, ZoneOffset.UTC)
            return if (sykmeldingTime.isBefore(currentTime)) {
                sykmeldingTime
            } else {
                log.info("Current time $currentTime is before mottatt sykmelding time $sykmeldingTime, setting status timestamp to current time")
                currentTime
            }
        }
    }
}
