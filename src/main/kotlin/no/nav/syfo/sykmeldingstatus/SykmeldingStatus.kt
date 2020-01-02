package no.nav.syfo.sykmeldingstatus

import java.time.LocalDateTime

data class SykmeldingStatus(
    val timestamp: LocalDateTime,
    val statusEvent: StatusEvent,
    val arbeidsgiver: ArbeidsgiverStatus?,
    val sporsmalListe: List<Sporsmal>?
)

data class SykmeldingStatusEvent(
    val sykmeldingId: String,
    val timestamp: LocalDateTime,
    val event: StatusEvent
)

enum class StatusEvent {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET, SLETTET
}

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: LocalDateTime
)

enum class StatusEventDTO {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET
}

data class SykmeldingSendEvent(
    val sykmeldingId: String,
    val timestamp: LocalDateTime,
    val arbeidsgiver: ArbeidsgiverStatus,
    val sporsmal: Sporsmal
)

data class ArbeidsgiverStatus(
    val sykmeldingId: String,
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgnavn: String
)

data class Sporsmal(
    val tekst: String,
    val shortName: ShortName,
    val svar: Svar
)

data class Svar(
    val sykmeldingId: String,
    val sporsmalId: Int?,
    val svartype: Svartype,
    val svar: String
)

enum class ShortName {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING
}

enum class Svartype {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI
}

data class SykmeldingBekreftEvent(
    val sykmeldingId: String,
    val timestamp: LocalDateTime,
    val sporsmal: List<Sporsmal>?
)
