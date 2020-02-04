package no.nav.syfo.sykmelding.internal.model

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import no.nav.syfo.aksessering.db.finnPeriodetype
import no.nav.syfo.domain.toDTO
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.ArbeidsrelatertArsakType
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskArsakType
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.Status
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sykmelding.internal.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.internal.db.StatusDbModel
import no.nav.syfo.sykmelding.internal.db.SykmeldingDbModel

internal fun SykmeldingDbModel.toInternalSykmelding(): InternalSykmeldingDTO {
    return InternalSykmeldingDTO(
            id = id,
            andreTiltak = sykmeldingsDokument.andreTiltak,
            skjermesForPasient = sykmeldingsDokument.skjermesForPasient,
            mottattTidspunkt = getUtcTime(mottattTidspunkt),
            legekontorOrgnummer = legekontorOrgNr,
            behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
            meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
            navnFastlege = sykmeldingsDokument.navnFastlege,
            tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
            syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
            tiltakNAV = sykmeldingsDokument.tiltakNAV,
            behandler = sykmeldingsDokument.behandler.toBehandlerDTO(),
            medisinskVurdering = sykmeldingsDokument.medisinskVurdering.toMedisinskVurderingDTO(),
            behandlingsutfall = behandlingsutfall.toBehandlingsutfallDTO(),
            sykmeldingStatus = status.toSykmeldingStatusDTO(),
            sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO() },
            arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
            kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
            meldingTilNAV = sykmeldingsDokument.meldingTilNAV?.toMeldingTilNavDTO(),
            prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
            utdypendeOpplysninger = toUtdypendeOpplysninger(sykmeldingsDokument.utdypendeOpplysninger)
            // TODO: legg til sendt, hør med veden
    )
}

fun getUtcTime(mottattTidspunkt: LocalDateTime): ZonedDateTime {
    return mottattTidspunkt.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC)
}

private fun StatusDbModel.toSykmeldingStatusDTO(): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(status, getUtcTime(status_timestamp), arbeidsgiver?.toArbeidsgiverStatusDTO())
}

private fun ArbeidsgiverDbModel.toArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgNr, juridiskOrgNr, navn)
}

fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): Map<String, Map<String, SporsmalSvarDTO>> {
    return utdypendeOpplysninger.mapValues {
        it.value.mapValues {
            entry -> entry.value.toSporsmalSvarDTO() }
                .filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_NAV) }
    }
}

private fun SporsmalSvar.toSporsmalSvarDTO(): SporsmalSvarDTO {
    return SporsmalSvarDTO(
            sporsmal = sporsmal,
            svar = svar,
            restriksjoner = restriksjoner.map { it.toSvarRestriksjonDTO() }

    )
}

private fun SvarRestriksjon.toSvarRestriksjonDTO(): SvarRestriksjonDTO {
    return when (this) {
        SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER -> SvarRestriksjonDTO.SKJERMET_FOR_ARBEIDSGIVER
        SvarRestriksjon.SKJERMET_FOR_NAV -> SvarRestriksjonDTO.SKJERMET_FOR_NAV
        SvarRestriksjon.SKJERMET_FOR_PASIENT -> SvarRestriksjonDTO.SKJERMET_FOR_PASIENT
    }
}

private fun Prognose.toPrognoseDTO(): PrognoseDTO {
    return PrognoseDTO(
            arbeidsforEtterPeriode = arbeidsforEtterPeriode,
            erIArbeid = erIArbeid?.toErIArbeidDTO(),
            erIkkeIArbeid = erIkkeIArbeid?.toErIkkeIArbeidDTO(),
            hensynArbeidsplassen = hensynArbeidsplassen
    )
}

private fun ErIkkeIArbeid.toErIkkeIArbeidDTO(): ErIkkeIArbeidDTO {
    return ErIkkeIArbeidDTO(
            arbeidsforPaSikt = arbeidsforPaSikt,
            arbeidsforFOM = arbeidsforFOM,
            vurderingsdato = vurderingsdato
    )
}

private fun ErIArbeid.toErIArbeidDTO(): ErIArbeidDTO {
    return ErIArbeidDTO(
            egetArbeidPaSikt = egetArbeidPaSikt,
            annetArbeidPaSikt = annetArbeidPaSikt,
            arbeidFOM = arbeidFOM,
            vurderingsdato = vurderingsdato
    )
}

private fun MeldingTilNAV.toMeldingTilNavDTO(): MeldingTilNavDTO? {
    return MeldingTilNavDTO(
            bistandUmiddelbart = bistandUmiddelbart,
            beskrivBistand = beskrivBistand
    )
}

private fun KontaktMedPasient.toKontaktMedPasientDTO(): KontaktMedPasientDTO {
    return KontaktMedPasientDTO(
            kontaktDato = kontaktDato,
            begrunnelseIkkeKontakt = begrunnelseIkkeKontakt
    )
}

private fun Arbeidsgiver.toArbeidsgiverDTO(): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(navn, stillingsprosent)
}

private fun Periode.toSykmeldingsperiodeDTO(): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
            fom = fom,
            tom = tom,
            behandlingsdager = behandlingsdager,
            gradert = gradert?.toGradertDTO(),
            innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
            type = finnPeriodetype(this).toDTO(),
            aktivitetIkkeMulig = aktivitetIkkeMulig?.toDto(),
            reisetilskudd = reisetilskudd
    )
}

private fun AktivitetIkkeMulig.toDto(): AktivitetIkkeMuligDTO {
    return AktivitetIkkeMuligDTO(medisinskArsak = medisinskArsak?.toMedisinskArsakDto(),
            arbeidsrelatertArsak = arbeidsrelatertArsak?.toArbeidsrelatertArsakDto())
}

private fun ArbeidsrelatertArsak.toArbeidsrelatertArsakDto(): ArbeidsrelatertArsakDTO {
    return ArbeidsrelatertArsakDTO(
            beskrivelse = beskrivelse,
            arsak = arsak.map { toArbeidsrelatertArsakTypeDto(it) }
    )
}

fun toArbeidsrelatertArsakTypeDto(arbeidsrelatertArsakType: ArbeidsrelatertArsakType): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun MedisinskArsak.toMedisinskArsakDto(): MedisinskArsakDTO {
    return MedisinskArsakDTO(
            beskrivelse = beskrivelse,
            arsak = arsak.map { toMedisinskArsakTypeDto(it) }
    )
}

fun toMedisinskArsakTypeDto(medisinskArsakType: MedisinskArsakType): MedisinskArsakTypeDTO {
    return when (medisinskArsakType) {
        MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakTypeDTO.AKTIVITET_FORHINDRER_BEDRING
        MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakTypeDTO.AKTIVITET_FORVERRER_TILSTAND
        MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakTypeDTO.TILSTAND_HINDRER_AKTIVITET
        MedisinskArsakType.ANNET -> MedisinskArsakTypeDTO.ANNET
    }
}

private fun Gradert.toGradertDTO(): GradertDTO {
    return GradertDTO(grad, reisetilskudd)
}

private fun ValidationResult.toBehandlingsutfallDTO(): BehandlingsutfallDTO {
    return BehandlingsutfallDTO(
            status = status.toRuleStatusDTO(),
            ruleHits = ruleHits.map { it.toRegeleinfoDTO() }
    )
}

private fun RuleInfo.toRegeleinfoDTO(): RegelinfoDTO {
    return RegelinfoDTO(
            messageForSender = messageForSender,
            messageForUser = messageForUser,
            ruleName = ruleName,
            ruleStatus = ruleStatus.toRuleStatusDTO()
    )
}

private fun Status.toRuleStatusDTO(): RegelStatusDTO {
    return when (this) {
        Status.OK -> RegelStatusDTO.OK
        Status.MANUAL_PROCESSING -> RegelStatusDTO.MANUAL_PROCESSING
        Status.INVALID -> RegelStatusDTO.INVALID
    }
}

private fun MedisinskVurdering.toMedisinskVurderingDTO(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
            hovedDiagnose = hovedDiagnose?.toDiagnoseDTO(),
            biDiagnoser = biDiagnoser.map { it.toDiagnoseDTO() }
    )
}

private fun Diagnose.toDiagnoseDTO(): DiagnoseDTO {
    return DiagnoseDTO(
            kode = kode,
            system = system,
            tekst = tekst
    )
}

private fun Behandler.toBehandlerDTO(): BehandlerDTO {
    return BehandlerDTO(
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            aktoerId = aktoerId,
            fnr = fnr,
            her = her,
            hpr = hpr,
            tlf = tlf,
            adresse = adresse.toAdresseDTO()
    )
}

private fun Adresse.toAdresseDTO(): AdresseDTO {
    return AdresseDTO(
            gate = gate,
            kommune = kommune,
            land = land,
            postboks = postboks,
            postnummer = postnummer
    )
}
