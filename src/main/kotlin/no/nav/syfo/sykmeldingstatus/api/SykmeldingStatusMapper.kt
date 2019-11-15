package no.nav.syfo.sykmeldingstatus.api

import no.nav.syfo.sykmeldingstatus.Arbeidsgiver
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent

fun tilSykmeldingSendEvent(sykmeldingId: String, sykmeldingSendEventDTO: SykmeldingSendEventDTO): SykmeldingSendEvent {
    val arbeidssituasjon: SporsmalOgSvarDTO = finnArbeidssituasjonSpm(sykmeldingSendEventDTO)

    return SykmeldingSendEvent(
        sykmeldingId,
        sykmeldingSendEventDTO.timestamp,
        tilArbeidsgiver(sykmeldingId, sykmeldingSendEventDTO.arbeidsgiver),
        tilSporsmal(sykmeldingId, arbeidssituasjon)
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

fun tilArbeidsgiver(sykmeldingsId: String, arbeidsgiverDTO: ArbeidsgiverRTDTO): Arbeidsgiver =
    Arbeidsgiver(
        sykmeldingId = sykmeldingsId,
        orgnavn = arbeidsgiverDTO.orgNavn,
        orgnummer = arbeidsgiverDTO.orgnummer,
        juridiskOrgnummer = arbeidsgiverDTO.juridiskOrgnummer
    )

fun tilSporsmal(sykmeldingId: String, arbeidssituasjon: SporsmalOgSvarDTO): Sporsmal =
    Sporsmal(tekst = arbeidssituasjon.tekst, shortName = arbeidssituasjon.shortName.tilShortName(), svar = tilSvar(sykmeldingId, arbeidssituasjon))

fun tilSvar(sykmeldingsId: String, arbeidssituasjon: SporsmalOgSvarDTO): Svar =
    Svar(sykmeldingId = sykmeldingsId, sporsmalId = null, svartype = arbeidssituasjon.svartype.tilSvartype(), svar = arbeidssituasjon.svar)

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
