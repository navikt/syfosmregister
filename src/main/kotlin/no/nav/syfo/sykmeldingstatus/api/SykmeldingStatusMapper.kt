package no.nav.syfo.sykmeldingstatus.api

import no.nav.syfo.sykmeldingstatus.ArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatus

fun tilSykmeldingSendEvent(sykmeldingId: String, sykmeldingSendEventDTO: SykmeldingSendEventDTO): SykmeldingSendEvent {
    val arbeidssituasjon: SporsmalOgSvarDTO = finnArbeidssituasjonSpm(sykmeldingSendEventDTO)

    return SykmeldingSendEvent(
        sykmeldingId,
        sykmeldingSendEventDTO.timestamp,
        tilArbeidsgiverStatus(sykmeldingId, sykmeldingSendEventDTO.arbeidsgiver),
        tilSporsmal(sykmeldingId, arbeidssituasjon)
    )
}

fun tilSykmeldingBekreftEvent(sykmeldingId: String, sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO): SykmeldingBekreftEvent {

    return SykmeldingBekreftEvent(
        sykmeldingId,
        sykmeldingBekreftEventDTO.timestamp,
        tilSporsmalListe(sykmeldingId, sykmeldingBekreftEventDTO.sporsmalOgSvarListe)
    )
}

fun tilSykmeldingStatusDTO(sykmeldingStatus: SykmeldingStatus): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        timestamp = sykmeldingStatus.timestamp,
        statusEvent = sykmeldingStatus.statusEvent.toStatusEventDTO(),
        arbeidsgiver = tilArbeidsgiverStatusDTO(sykmeldingStatus.arbeidsgiver),
        sporsmalOgSvarListe = tilSporsmalOgSvarDTOListe(sykmeldingStatus.sporsmalListe)
    )
}

fun StatusEventDTO.toStatusEvent(): StatusEvent {
    return when (this) {
        StatusEventDTO.BEKREFTET -> StatusEvent.BEKREFTET
        StatusEventDTO.APEN -> StatusEvent.APEN
        StatusEventDTO.SENDT -> StatusEvent.SENDT
        StatusEventDTO.AVBRUTT -> StatusEvent.AVBRUTT
        StatusEventDTO.UTGATT -> StatusEvent.UTGATT
    }
}

fun StatusEvent.toStatusEventDTO(): StatusEventDTO {
    return when (this) {
        StatusEvent.BEKREFTET -> StatusEventDTO.BEKREFTET
        StatusEvent.APEN -> StatusEventDTO.APEN
        StatusEvent.SENDT -> StatusEventDTO.SENDT
        StatusEvent.AVBRUTT -> StatusEventDTO.AVBRUTT
        StatusEvent.UTGATT -> StatusEventDTO.UTGATT
        StatusEvent.SLETTET -> throw IllegalStateException("Sykmeldingen er slettet, skal ikke kunne skje")
    }
}

fun tilArbeidsgiverStatus(sykmeldingsId: String, arbeidsgiver: ArbeidsgiverStatusDTO): ArbeidsgiverStatus =
    ArbeidsgiverStatus(
        sykmeldingId = sykmeldingsId,
        orgnavn = arbeidsgiver.orgNavn,
        orgnummer = arbeidsgiver.orgnummer,
        juridiskOrgnummer = arbeidsgiver.juridiskOrgnummer
    )

fun tilArbeidsgiverStatusDTO(arbeidsgiver: ArbeidsgiverStatus?): ArbeidsgiverStatusDTO? =
    arbeidsgiver?.let {
        ArbeidsgiverStatusDTO(
            orgNavn = arbeidsgiver.orgnavn,
            orgnummer = arbeidsgiver.orgnummer,
            juridiskOrgnummer = arbeidsgiver.juridiskOrgnummer
        )
    }

fun tilSporsmalListe(sykmeldingId: String, sporsmalOgSvarDTO: List<SporsmalOgSvarDTO>?): List<Sporsmal>? {
    return if (sporsmalOgSvarDTO.isNullOrEmpty()) {
        null
    } else {
        sporsmalOgSvarDTO.map { tilSporsmal(sykmeldingId, it) }
    }
}

fun tilSporsmal(sykmeldingId: String, sporsmalOgSvarDTO: SporsmalOgSvarDTO): Sporsmal =
    Sporsmal(tekst = sporsmalOgSvarDTO.tekst, shortName = sporsmalOgSvarDTO.shortName.tilShortName(), svar = tilSvar(sykmeldingId, sporsmalOgSvarDTO))

fun tilSvar(sykmeldingsId: String, sporsmalOgSvarDTO: SporsmalOgSvarDTO): Svar =
    Svar(sykmeldingId = sykmeldingsId, sporsmalId = null, svartype = sporsmalOgSvarDTO.svartype.tilSvartype(), svar = sporsmalOgSvarDTO.svar)

fun tilSporsmalOgSvarDTOListe(sporsmalListe: List<Sporsmal>?): List<SporsmalOgSvarDTO>? {
    return if (sporsmalListe.isNullOrEmpty()) {
        null
    } else {
        sporsmalListe.map { tilSporsmalOgSvarDTO(it) }
    }
}

fun tilSporsmalOgSvarDTO(sporsmal: Sporsmal): SporsmalOgSvarDTO =
    SporsmalOgSvarDTO(tekst = sporsmal.tekst, shortName = sporsmal.shortName.tilShortNameDTO(), svartype = sporsmal.svar.svartype.tilSvartypeDTO(), svar = sporsmal.svar.svar)

private fun finnArbeidssituasjonSpm(sykmeldingSendEvent: SykmeldingSendEventDTO) =
    sykmeldingSendEvent.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.ARBEIDSSITUASJON } ?: throw IllegalStateException("Mangler informasjon om arbeidssituasjon")

private fun ShortNameDTO.tilShortName(): ShortName {
    return when (this) {
        ShortNameDTO.ARBEIDSSITUASJON -> ShortName.ARBEIDSSITUASJON
        ShortNameDTO.FORSIKRING -> ShortName.FORSIKRING
        ShortNameDTO.FRAVAER -> ShortName.FRAVAER
        ShortNameDTO.PERIODE -> ShortName.PERIODE
        ShortNameDTO.NY_NARMESTE_LEDER -> ShortName.NY_NARMESTE_LEDER
    }
}

private fun ShortName.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
        ShortName.FRAVAER -> ShortNameDTO.FRAVAER
        ShortName.PERIODE -> ShortNameDTO.PERIODE
        ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

private fun SvartypeDTO.tilSvartype(): Svartype {
    return when (this) {
        SvartypeDTO.ARBEIDSSITUASJON -> Svartype.ARBEIDSSITUASJON
        SvartypeDTO.JA_NEI -> Svartype.JA_NEI
        SvartypeDTO.PERIODER -> Svartype.PERIODER
    }
}

private fun Svartype.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        Svartype.PERIODER -> SvartypeDTO.PERIODER
    }
}
