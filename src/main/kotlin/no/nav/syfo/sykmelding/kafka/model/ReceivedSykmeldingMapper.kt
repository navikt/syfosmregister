package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.ArbeidsrelatertArsakType
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sykmelding.db.Periodetype
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AktivitetIkkeMuligDTO
import no.nav.syfo.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.ArbeidsrelatertArsakDTO
import no.nav.syfo.sykmelding.model.ArbeidsrelatertArsakTypeDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.ErIArbeidDTO
import no.nav.syfo.sykmelding.model.ErIkkeIArbeidDTO
import no.nav.syfo.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskArsakDTO
import no.nav.syfo.sykmelding.model.MedisinskArsakTypeDTO
import no.nav.syfo.sykmelding.model.MerknadDTO
import no.nav.syfo.sykmelding.model.PrognoseDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.getUtcTime
import no.nav.syfo.sykmelding.model.toDTO

fun ReceivedSykmelding.toEnkelSykmelding(): EnkelSykmelding {
    return EnkelSykmelding(
            id = sykmelding.id,
            tiltakArbeidsplassen = sykmelding.tiltakArbeidsplassen,
            syketilfelleStartDato = sykmelding.syketilfelleStartDato,
            prognose = sykmelding.prognose.toPrognoseDTO(),
            navnFastlege = sykmelding.navnFastlege,
            meldingTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
            kontaktMedPasient = sykmelding.kontaktMedPasient.toKontaktMedPasientDto(),
            arbeidsgiver = sykmelding.arbeidsgiver.toArbeidsgiverDto(),
            behandler = sykmelding.behandler.toBehandlerDto(),
            behandletTidspunkt = getUtcTime(sykmelding.behandletTidspunkt),
            egenmeldt = sykmelding.avsenderSystem.navn == "Egenmeldt",
            papirsykmelding = sykmelding.avsenderSystem.navn == "Papirsykmelding",
            legekontorOrgnummer = legekontorOrgNr,
            mottattTidspunkt = getUtcTime(mottattDato),
            sykmeldingsperioder = sykmelding.perioder.map { it.toPeriodeDto() },
            harRedusertArbeidsgiverperiode = sykmelding.medisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmelding.perioder),
            merknader = merknader?.map { MerknadDTO(type = it.type, beskrivelse = it.beskrivelse) }
    )
}

private fun Periode.toPeriodeDto(): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
            fom = fom,
            tom = tom,
            behandlingsdager = behandlingsdager,
            innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
            type = finnPeriodetype(this).toDTO(),
            reisetilskudd = reisetilskudd,
            gradert = gradert.toGradertDto(),
            aktivitetIkkeMulig = aktivitetIkkeMulig.toAktivitetIkkeMuligDto()
    )
}

private fun finnPeriodetype(periode: Periode): Periodetype =
    when {
        periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
        periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Periodetype.GRADERT
        periode.reisetilskudd -> Periodetype.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }

private fun AktivitetIkkeMulig?.toAktivitetIkkeMuligDto(): AktivitetIkkeMuligDTO? {
    return when (this) {
        null -> null
        else -> AktivitetIkkeMuligDTO(
                medisinskArsak = medisinskArsak.toMedisinskArsakDto(),
                arbeidsrelatertArsak = arbeidsrelatertArsak.toArbeidsRelatertArsakDto()
        )
    }
}

private fun ArbeidsrelatertArsak?.toArbeidsRelatertArsakDto(): ArbeidsrelatertArsakDTO? {
    return when (this) {
        null -> null
        else -> ArbeidsrelatertArsakDTO(
                beskrivelse = beskrivelse,
                arsak = arsak.map { toArbeidsrelatertArsakTypeDto(it) }
        )
    }
}

private fun toArbeidsrelatertArsakTypeDto(arbeidsrelatertArsakType: ArbeidsrelatertArsakType): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun MedisinskArsak?.toMedisinskArsakDto(): MedisinskArsakDTO? {
    return when (this) {
        null -> null
        else -> MedisinskArsakDTO(
                beskrivelse = beskrivelse,
                arsak = arsak.map { toMedisinskArsakTypeDto(it) }
        )
    }
}

private fun toMedisinskArsakTypeDto(medisinskArsakType: MedisinskArsakType): MedisinskArsakTypeDTO {
    return when (medisinskArsakType) {
        MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakTypeDTO.TILSTAND_HINDRER_AKTIVITET
        MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakTypeDTO.AKTIVITET_FORVERRER_TILSTAND
        MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakTypeDTO.AKTIVITET_FORHINDRER_BEDRING
        MedisinskArsakType.ANNET -> MedisinskArsakTypeDTO.ANNET
    }
}

private fun Gradert?.toGradertDto(): GradertDTO? {
    return when (this) {
        null -> null
        else -> GradertDTO(
                grad = grad,
                reisetilskudd = reisetilskudd
        )
    }
}

private fun Behandler.toBehandlerDto(): BehandlerDTO {
    return BehandlerDTO(
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            hpr = hpr,
            tlf = tlf,
            her = her,
            aktoerId = aktoerId,
            etternavn = etternavn,
            fnr = fnr,
            adresse = AdresseDTO(adresse.gate, adresse.postnummer, adresse.kommune, adresse.postboks, adresse.land)
    )
}

private fun Arbeidsgiver.toArbeidsgiverDto(): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(
            navn = navn,
            stillingsprosent = stillingsprosent
    )
}

private fun KontaktMedPasient.toKontaktMedPasientDto(): KontaktMedPasientDTO {
    return KontaktMedPasientDTO(
            kontaktDato = kontaktDato,
            begrunnelseIkkeKontakt = begrunnelseIkkeKontakt
    )
}

private fun Prognose?.toPrognoseDTO(): PrognoseDTO? {
    return when (this) {
        null -> null
        else -> {
            PrognoseDTO(
                    arbeidsforEtterPeriode = arbeidsforEtterPeriode,
                    erIArbeid = erIArbeid.toErIArbeidDTO(),
                    erIkkeIArbeid = erIkkeIArbeid.toErIkkeIArbeidDto(),
                    hensynArbeidsplassen = hensynArbeidsplassen
            )
        }
    }
}

private fun ErIkkeIArbeid?.toErIkkeIArbeidDto(): ErIkkeIArbeidDTO? {
    return when (this) {
        null -> null
        else -> ErIkkeIArbeidDTO(
                arbeidsforPaSikt = arbeidsforPaSikt,
                vurderingsdato = vurderingsdato,
                arbeidsforFOM = arbeidsforFOM
        )
    }
}

private fun ErIArbeid?.toErIArbeidDTO(): ErIArbeidDTO? {
    return when (this) {
        null -> null
        else -> ErIArbeidDTO(
                egetArbeidPaSikt = egetArbeidPaSikt,
                vurderingsdato = vurderingsdato,
                annetArbeidPaSikt = annetArbeidPaSikt,
                arbeidFOM = arbeidFOM
        )
    }
}
