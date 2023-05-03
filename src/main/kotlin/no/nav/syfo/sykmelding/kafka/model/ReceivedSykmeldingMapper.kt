package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.ArbeidsrelatertArsakType
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.AktivitetIkkeMuligAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.PrognoseAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakDTO
import no.nav.syfo.model.sykmelding.model.ArbeidsrelatertArsakTypeDTO
import no.nav.syfo.model.sykmelding.model.GradertDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.getUtcTime

fun ReceivedSykmelding.toArbeidsgiverSykmelding(): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = sykmelding.id,
        tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
        syketilfelleStartDato = sykmelding.syketilfelleStartDato,
        prognose = sykmelding.prognose.toPrognoseAGDTO(),
        meldingTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
        kontaktMedPasient = sykmelding.kontaktMedPasient.toKontaktMedPasientAGDTO(),
        arbeidsgiver = sykmelding.arbeidsgiver.toArbeidsgiverAGDTO(),
        behandler = if (utenlandskSykmelding != null) {
            null
        } else {
            sykmelding.behandler.toBehandlerAGDTO()
        },
        behandletTidspunkt = getUtcTime(sykmelding.behandletTidspunkt),
        egenmeldt = sykmelding.avsenderSystem.navn == "Egenmeldt",
        papirsykmelding = sykmelding.avsenderSystem.navn == "Papirsykmelding",
        mottattTidspunkt = getUtcTime(mottattDato),
        sykmeldingsperioder = sykmelding.perioder.map { it.toPeriodeAGDTO() },
        harRedusertArbeidsgiverperiode = sykmelding.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmelding.perioder),
        merknader = merknader?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) },
        utenlandskSykmelding = utenlandskSykmelding?.let { UtenlandskSykmeldingAGDTO(land = it.land) },
    )
}

private fun Periode.toPeriodeAGDTO(): SykmeldingsperiodeAGDTO {
    return SykmeldingsperiodeAGDTO(
        fom = fom,
        tom = tom,
        behandlingsdager = behandlingsdager,
        innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(this),
        reisetilskudd = reisetilskudd,
        gradert = gradert.toGradertDTO(),
        aktivitetIkkeMulig = aktivitetIkkeMulig.toAktivitetIkkeMuligAGDTO(),
    )
}

private fun finnPeriodetype(periode: Periode): PeriodetypeDTO =
    when {
        periode.aktivitetIkkeMulig != null -> PeriodetypeDTO.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> PeriodetypeDTO.AVVENTENDE
        periode.behandlingsdager != null -> PeriodetypeDTO.BEHANDLINGSDAGER
        periode.gradert != null -> PeriodetypeDTO.GRADERT
        periode.reisetilskudd -> PeriodetypeDTO.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }

private fun AktivitetIkkeMulig?.toAktivitetIkkeMuligAGDTO(): AktivitetIkkeMuligAGDTO? {
    return when (this) {
        null -> null
        else -> AktivitetIkkeMuligAGDTO(
            arbeidsrelatertArsak = arbeidsrelatertArsak.toArbeidsRelatertArsakDTO(),
        )
    }
}

private fun ArbeidsrelatertArsak?.toArbeidsRelatertArsakDTO(): ArbeidsrelatertArsakDTO? {
    return when (this) {
        null -> null
        else -> ArbeidsrelatertArsakDTO(
            beskrivelse = beskrivelse,
            arsak = arsak.map { toArbeidsrelatertArsakTypeDTO(it) },
        )
    }
}

private fun toArbeidsrelatertArsakTypeDTO(arbeidsrelatertArsakType: ArbeidsrelatertArsakType): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun Gradert?.toGradertDTO(): GradertDTO? {
    return when (this) {
        null -> null
        else -> GradertDTO(
            grad = grad,
            reisetilskudd = reisetilskudd,
        )
    }
}

private fun Behandler.toBehandlerAGDTO(): BehandlerAGDTO {
    return BehandlerAGDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        hpr = hpr,
        tlf = tlf,
        etternavn = etternavn,
        adresse = AdresseDTO(adresse.gate, adresse.postnummer, adresse.kommune, adresse.postboks, adresse.land),
    )
}

private fun Arbeidsgiver.toArbeidsgiverAGDTO(): ArbeidsgiverAGDTO {
    return ArbeidsgiverAGDTO(
        navn = navn,
        yrkesbetegnelse = yrkesbetegnelse,
    )
}

private fun KontaktMedPasient.toKontaktMedPasientAGDTO(): KontaktMedPasientAGDTO {
    return KontaktMedPasientAGDTO(
        kontaktDato = kontaktDato,
    )
}

private fun Prognose?.toPrognoseAGDTO(): PrognoseAGDTO? {
    return when (this) {
        null -> null
        else -> {
            PrognoseAGDTO(
                arbeidsforEtterPeriode = arbeidsforEtterPeriode,
                hensynArbeidsplassen = hensynArbeidsplassen,
            )
        }
    }
}
