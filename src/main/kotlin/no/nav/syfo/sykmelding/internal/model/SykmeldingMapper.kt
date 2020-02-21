package no.nav.syfo.sykmelding.internal.model

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.domain.Periodetype
import no.nav.syfo.domain.toDTO
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sykmelding.internal.db.Adresse
import no.nav.syfo.sykmelding.internal.db.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.internal.db.AnnenFraverGrunn
import no.nav.syfo.sykmelding.internal.db.AnnenFraversArsak
import no.nav.syfo.sykmelding.internal.db.Arbeidsgiver
import no.nav.syfo.sykmelding.internal.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.internal.db.ArbeidsrelatertArsak
import no.nav.syfo.sykmelding.internal.db.ArbeidsrelatertArsakType
import no.nav.syfo.sykmelding.internal.db.Behandler
import no.nav.syfo.sykmelding.internal.db.Diagnose
import no.nav.syfo.sykmelding.internal.db.ErIArbeid
import no.nav.syfo.sykmelding.internal.db.ErIkkeIArbeid
import no.nav.syfo.sykmelding.internal.db.Gradert
import no.nav.syfo.sykmelding.internal.db.KontaktMedPasient
import no.nav.syfo.sykmelding.internal.db.MedisinskArsak
import no.nav.syfo.sykmelding.internal.db.MedisinskArsakType
import no.nav.syfo.sykmelding.internal.db.MedisinskVurdering
import no.nav.syfo.sykmelding.internal.db.MeldingTilNAV
import no.nav.syfo.sykmelding.internal.db.Periode
import no.nav.syfo.sykmelding.internal.db.Prognose
import no.nav.syfo.sykmelding.internal.db.SporsmalSvar
import no.nav.syfo.sykmelding.internal.db.StatusDbModel
import no.nav.syfo.sykmelding.internal.db.SvarRestriksjon
import no.nav.syfo.sykmelding.internal.db.SykmeldingDbModel
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO

internal fun SykmeldingDbModel.toInternalSykmelding(sporsmal: List<Sporsmal>): InternalSykmeldingDTO {
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
            sykmeldingStatus = status.toSykmeldingStatusDTO(sporsmal.map { it.toSporsmalDTO() }),
            sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO() },
            arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
            kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
            meldingTilNAV = sykmeldingsDokument.meldingTilNAV?.toMeldingTilNavDTO(),
            prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
            utdypendeOpplysninger = toUtdypendeOpplysninger(sykmeldingsDokument.utdypendeOpplysninger)
    )
}

private fun Sporsmal.toSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(
            tekst = tekst,
            svar = svar.toDTO(),
            shortName = shortName.toDTO()
    )
}

private fun ShortName.toDTO(): ShortNameDTO {
    return when (this) {
        ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
        ShortName.FRAVAER -> ShortNameDTO.FRAVAER
        ShortName.PERIODE -> ShortNameDTO.PERIODE
        ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

private fun Svar.toDTO(): SvarDTO {
    return SvarDTO(
            svar = svar,
            svarType = svartype.toDTO()
    )
}

private fun Svartype.toDTO(): SvartypeDTO {
    return when (this) {
        Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        Svartype.PERIODER -> SvartypeDTO.PERIODER
    }
}

fun getUtcTime(tidspunkt: LocalDateTime): OffsetDateTime {
    return tidspunkt.atOffset(ZoneOffset.UTC)
}

private fun StatusDbModel.toSykmeldingStatusDTO(sporsmal: List<SporsmalDTO>): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(statusEvent, getUtcTime(statusTimestamp), arbeidsgiver?.toArbeidsgiverStatusDTO(), sporsmal)
}

private fun ArbeidsgiverDbModel.toArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer, juridiskOrgnummer, orgNavn)
}

fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): Map<String, Map<String, SporsmalSvarDTO>> {
    return utdypendeOpplysninger.mapValues {
        it.value.mapValues { entry -> entry.value.toSporsmalSvarDTO() }
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
            biDiagnoser = biDiagnoser.map {
                it.toDiagnoseDTO()
            },

            annenFraversArsak = annenFraversArsak?.toDTO(),
            svangerskap = svangerskap,
            yrkesskade = yrkesskade,
            yrkesskadeDato = yrkesskadeDato)
}

private fun AnnenFraversArsak.toDTO(): AnnenFraversArsakDTO {
    return AnnenFraversArsakDTO(
            beskrivelse = beskrivelse,
            grunn = grunn.map { it.toDTO() }
    )
}

private fun AnnenFraverGrunn.toDTO(): AnnenFraverGrunnDTO {
    return when (this) {
        AnnenFraverGrunn.ABORT -> AnnenFraverGrunnDTO.ABORT
        AnnenFraverGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraverGrunnDTO.ARBEIDSRETTET_TILTAK
        AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID -> AnnenFraverGrunnDTO.BEHANDLING_FORHINDRER_ARBEID
        AnnenFraverGrunn.BEHANDLING_STERILISERING -> AnnenFraverGrunnDTO.BEHANDLING_STERILISERING
        AnnenFraverGrunn.DONOR -> AnnenFraverGrunnDTO.DONOR
        AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON -> AnnenFraverGrunnDTO.GODKJENT_HELSEINSTITUSJON
        AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND -> AnnenFraverGrunnDTO.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
        AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE -> AnnenFraverGrunnDTO.NODVENDIG_KONTROLLUNDENRSOKELSE
        AnnenFraverGrunn.SMITTEFARE -> AnnenFraverGrunnDTO.SMITTEFARE
        AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraverGrunnDTO.UFOR_GRUNNET_BARNLOSHET
    }
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

fun finnPeriodetype(periode: Periode): Periodetype =
        when {
            periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
            periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
            periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
            periode.gradert != null -> Periodetype.GRADERT
            periode.reisetilskudd -> Periodetype.REISETILSKUDD
            else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
        }
