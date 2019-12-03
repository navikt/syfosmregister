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

fun StatusEventDTO.toStatusEvent(): StatusEvent {
    return when (this) {
        StatusEventDTO.BEKREFTET -> StatusEvent.BEKREFTET
        StatusEventDTO.APEN -> StatusEvent.APEN
        StatusEventDTO.SENDT -> StatusEvent.SENDT
        StatusEventDTO.AVBRUTT -> StatusEvent.AVBRUTT
        StatusEventDTO.UTGATT -> StatusEvent.UTGATT
    }
}

fun tilArbeidsgiverStatus(sykmeldingsId: String, arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO): ArbeidsgiverStatus =
    ArbeidsgiverStatus(
        sykmeldingId = sykmeldingsId,
        orgnavn = arbeidsgiverStatusDTO.orgNavn,
        orgnummer = arbeidsgiverStatusDTO.orgnummer,
        juridiskOrgnummer = arbeidsgiverStatusDTO.juridiskOrgnummer
    )

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

private fun SvartypeDTO.tilSvartype(): Svartype {
    return when (this) {
        SvartypeDTO.ARBEIDSSITUASJON -> Svartype.ARBEIDSSITUASJON
        SvartypeDTO.JA_NEI -> Svartype.JA_NEI
        SvartypeDTO.PERIODE -> Svartype.PERIODE
        SvartypeDTO.PERIODER -> Svartype.PERIODER
    }
}
