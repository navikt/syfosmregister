package no.nav.syfo.sykmelding.status

import java.time.OffsetDateTime

data class SykmeldingStatusEvent(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val event: StatusEvent,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

enum class StatusEvent {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET, SLETTET
}

enum class StatusEventDTO {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET, SLETTET
}

data class SykmeldingSendEvent(
    val sykmeldingId: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatus,
    val sporsmal: List<Sporsmal>
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
    val timestamp: OffsetDateTime,
    val sporsmal: List<Sporsmal>?
)
