package no.nav.syfo.sykmelding.model

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.db.Adresse
import no.nav.syfo.sykmelding.db.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.db.AnnenFraverGrunn
import no.nav.syfo.sykmelding.db.AnnenFraversArsak
import no.nav.syfo.sykmelding.db.Arbeidsgiver
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.ArbeidsrelatertArsak
import no.nav.syfo.sykmelding.db.ArbeidsrelatertArsakType
import no.nav.syfo.sykmelding.db.Behandler
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.ErIArbeid
import no.nav.syfo.sykmelding.db.ErIkkeIArbeid
import no.nav.syfo.sykmelding.db.Gradert
import no.nav.syfo.sykmelding.db.KontaktMedPasient
import no.nav.syfo.sykmelding.db.MedisinskArsak
import no.nav.syfo.sykmelding.db.MedisinskArsakType
import no.nav.syfo.sykmelding.db.MedisinskVurdering
import no.nav.syfo.sykmelding.db.MeldingTilNAV
import no.nav.syfo.sykmelding.db.Periode
import no.nav.syfo.sykmelding.db.Periodetype
import no.nav.syfo.sykmelding.db.Prognose
import no.nav.syfo.sykmelding.db.SporsmalSvar
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.SvarRestriksjon
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.kafka.model.getHarRedusertArbeidsgiverperiode
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.api.ArbeidsgiverStatusDTO

internal fun SykmeldingDbModel.toSykmeldingDTO(
    sporsmal: List<Sporsmal>,
    isPasient: Boolean = false,
    ikkeTilgangTilDiagnose: Boolean,
    fullBehandler: Boolean = true
): SykmeldingDTO {
    val skjermetForPasient = sykmeldingsDokument.skjermesForPasient
    val skalFjerneSensitivInformasjon = (isPasient && skjermetForPasient) || ikkeTilgangTilDiagnose
    return SykmeldingDTO(
        id = id,
        andreTiltak =
            if (skalFjerneSensitivInformasjon) {
                null
            } else {
                sykmeldingsDokument.andreTiltak
            },
        skjermesForPasient = skjermetForPasient,
        mottattTidspunkt = mottattTidspunkt,
        legekontorOrgnummer = legekontorOrgNr,
        behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
        meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
        navnFastlege = sykmeldingsDokument.navnFastlege,
        tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
        syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
        tiltakNAV =
            if (skalFjerneSensitivInformasjon) {
                null
            } else {
                sykmeldingsDokument.tiltakNAV
            },
        behandler = sykmeldingsDokument.behandler.toBehandlerDTO(fullBehandler),
        medisinskVurdering =
            if (skalFjerneSensitivInformasjon) {
                null
            } else {
                sykmeldingsDokument.medisinskVurdering.toMedisinskVurderingDTO()
            },
        behandlingsutfall = behandlingsutfall.toBehandlingsutfallDTO(isPasient),
        sykmeldingStatus = status.toSykmeldingStatusDTO(sporsmal.map { it.toSporsmalDTO() }),
        sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO(id) },
        arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
        kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
        meldingTilNAV =
            if (skalFjerneSensitivInformasjon) {
                null
            } else {
                sykmeldingsDokument.meldingTilNAV?.toMeldingTilNavDTO()
            },
        prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
        utdypendeOpplysninger =
            if (skalFjerneSensitivInformasjon) {
                emptyMap()
            } else {
                toUtdypendeOpplysninger(sykmeldingsDokument.utdypendeOpplysninger, isPasient)
            },
        egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
        papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
        harRedusertArbeidsgiverperiode =
            sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode(
                sykmeldingsDokument.perioder
            ),
        merknader = merknader?.map { MerknadDTO(type = it.type, beskrivelse = it.beskrivelse) },
        utenlandskSykmelding = utenlandskSykmelding?.let { UtenlandskSykmeldingDTO(it.land) },
    )
}

fun Sporsmal.toSporsmalDTO(): SporsmalDTO {
    return SporsmalDTO(
        tekst = tekst,
        svar = svar.toDTO(),
        shortName = shortName.toDTO(),
    )
}

private fun ShortName.toDTO(): ShortNameDTO {
    return when (this) {
        ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
        ShortName.FRAVAER -> ShortNameDTO.FRAVAER
        ShortName.PERIODE -> ShortNameDTO.PERIODE
        ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
        ShortName.EGENMELDINGSDAGER -> ShortNameDTO.EGENMELDINGSDAGER
    }
}

private fun Svar.toDTO(): SvarDTO {
    return SvarDTO(
        svar = svar,
        svarType = svartype.toDTO(),
    )
}

private fun Svartype.toDTO(): SvartypeDTO {
    return when (this) {
        Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        Svartype.JA_NEI -> SvartypeDTO.JA_NEI
        Svartype.PERIODER -> SvartypeDTO.PERIODER
        Svartype.DAGER -> SvartypeDTO.DAGER
    }
}

fun getUtcTime(tidspunkt: LocalDateTime): OffsetDateTime {
    return tidspunkt.atOffset(ZoneOffset.UTC)
}

fun StatusDbModel.toSykmeldingStatusDTO(sporsmal: List<SporsmalDTO>): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
        statusEvent,
        statusTimestamp,
        arbeidsgiver?.toArbeidsgiverStatusDTO(),
        sporsmal
    )
}

private fun ArbeidsgiverDbModel.toArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer, juridiskOrgnummer, orgNavn)
}

fun toUtdypendeOpplysninger(
    utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>,
    isPasient: Boolean
): Map<String, Map<String, SporsmalSvarDTO>> {
    if (isPasient) {
        return utdypendeOpplysninger.mapValues {
            it.value
                .mapValues { entry -> entry.value.toSporsmalSvarDTO() }
                .filterValues { sporsmalSvar ->
                    !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_PASIENT)
                }
        }
    }
    return utdypendeOpplysninger.mapValues {
        it.value
            .mapValues { entry -> entry.value.toSporsmalSvarDTO() }
            .filterValues { sporsmalSvar ->
                !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_NAV)
            }
    }
}

fun SporsmalSvar.toSporsmalSvarDTO(): SporsmalSvarDTO {
    return SporsmalSvarDTO(
        sporsmal = sporsmal,
        svar = svar,
        restriksjoner = restriksjoner.map { it.toSvarRestriksjonDTO() },
    )
}

private fun SvarRestriksjon.toSvarRestriksjonDTO(): SvarRestriksjonDTO {
    return when (this) {
        SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER -> SvarRestriksjonDTO.SKJERMET_FOR_ARBEIDSGIVER
        SvarRestriksjon.SKJERMET_FOR_NAV -> SvarRestriksjonDTO.SKJERMET_FOR_NAV
        SvarRestriksjon.SKJERMET_FOR_PASIENT -> SvarRestriksjonDTO.SKJERMET_FOR_PASIENT
    }
}

fun Prognose.toPrognoseDTO(): PrognoseDTO {
    return PrognoseDTO(
        arbeidsforEtterPeriode = arbeidsforEtterPeriode,
        erIArbeid = erIArbeid?.toErIArbeidDTO(),
        erIkkeIArbeid = erIkkeIArbeid?.toErIkkeIArbeidDTO(),
        hensynArbeidsplassen = hensynArbeidsplassen,
    )
}

private fun ErIkkeIArbeid.toErIkkeIArbeidDTO(): ErIkkeIArbeidDTO {
    return ErIkkeIArbeidDTO(
        arbeidsforPaSikt = arbeidsforPaSikt,
        arbeidsforFOM = arbeidsforFOM,
        vurderingsdato = vurderingsdato,
    )
}

private fun ErIArbeid.toErIArbeidDTO(): ErIArbeidDTO {
    return ErIArbeidDTO(
        egetArbeidPaSikt = egetArbeidPaSikt,
        annetArbeidPaSikt = annetArbeidPaSikt,
        arbeidFOM = arbeidFOM,
        vurderingsdato = vurderingsdato,
    )
}

private fun MeldingTilNAV.toMeldingTilNavDTO(): MeldingTilNavDTO {
    return MeldingTilNavDTO(
        bistandUmiddelbart = bistandUmiddelbart,
        beskrivBistand = beskrivBistand,
    )
}

fun KontaktMedPasient.toKontaktMedPasientDTO(): KontaktMedPasientDTO {
    return KontaktMedPasientDTO(
        kontaktDato = kontaktDato,
        begrunnelseIkkeKontakt = begrunnelseIkkeKontakt,
    )
}

fun Arbeidsgiver.toArbeidsgiverDTO(): ArbeidsgiverDTO {
    return ArbeidsgiverDTO(navn, yrkesbetegnelse, stillingsprosent)
}

fun Periode.toSykmeldingsperiodeDTO(sykmeldingId: String): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
        fom = fom,
        tom = tom,
        behandlingsdager = behandlingsdager,
        gradert = gradert?.toGradertDTO(),
        innspillTilArbeidsgiver = avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(this, sykmeldingId).toDTO(),
        aktivitetIkkeMulig = aktivitetIkkeMulig?.toDto(),
        reisetilskudd = reisetilskudd,
    )
}

fun Periodetype.toDTO(): PeriodetypeDTO = PeriodetypeDTO.valueOf(this.name)

private fun AktivitetIkkeMulig.toDto(): AktivitetIkkeMuligDTO {
    return AktivitetIkkeMuligDTO(
        medisinskArsak = medisinskArsak?.toMedisinskArsakDto(),
        arbeidsrelatertArsak = arbeidsrelatertArsak?.toArbeidsrelatertArsakDto(),
    )
}

private fun ArbeidsrelatertArsak.toArbeidsrelatertArsakDto(): ArbeidsrelatertArsakDTO {
    return ArbeidsrelatertArsakDTO(
        beskrivelse = beskrivelse,
        arsak = arsak.map { toArbeidsrelatertArsakTypeDto(it) },
    )
}

fun toArbeidsrelatertArsakTypeDto(
    arbeidsrelatertArsakType: ArbeidsrelatertArsakType
): ArbeidsrelatertArsakTypeDTO {
    return when (arbeidsrelatertArsakType) {
        ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING ->
            ArbeidsrelatertArsakTypeDTO.MANGLENDE_TILRETTELEGGING
        ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakTypeDTO.ANNET
    }
}

private fun MedisinskArsak.toMedisinskArsakDto(): MedisinskArsakDTO {
    return MedisinskArsakDTO(
        beskrivelse = beskrivelse,
        arsak = arsak.map { toMedisinskArsakTypeDto(it) },
    )
}

fun toMedisinskArsakTypeDto(medisinskArsakType: MedisinskArsakType): MedisinskArsakTypeDTO {
    return when (medisinskArsakType) {
        MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING ->
            MedisinskArsakTypeDTO.AKTIVITET_FORHINDRER_BEDRING
        MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND ->
            MedisinskArsakTypeDTO.AKTIVITET_FORVERRER_TILSTAND
        MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET ->
            MedisinskArsakTypeDTO.TILSTAND_HINDRER_AKTIVITET
        MedisinskArsakType.ANNET -> MedisinskArsakTypeDTO.ANNET
    }
}

private fun Gradert.toGradertDTO(): GradertDTO {
    return GradertDTO(grad, reisetilskudd)
}

fun ValidationResult.toBehandlingsutfallDTO(isPasient: Boolean): BehandlingsutfallDTO {
    return BehandlingsutfallDTO(
        status = status.toRuleStatusDTO(),
        ruleHits =
            ruleHits
                .map { it.toRegeleinfoDTO() }
                .filterNot { it.ruleStatus == RegelStatusDTO.MANUAL_PROCESSING && isPasient },
    )
}

private fun RuleInfo.toRegeleinfoDTO(): RegelinfoDTO {
    return RegelinfoDTO(
        messageForSender = messageForSender,
        messageForUser = messageForUser,
        ruleName = ruleName,
        ruleStatus = ruleStatus.toRuleStatusDTO(),
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
        biDiagnoser = biDiagnoser.map { it.toDiagnoseDTO() },
        annenFraversArsak = annenFraversArsak?.toDTO(),
        svangerskap = svangerskap,
        yrkesskade = yrkesskade,
        yrkesskadeDato = yrkesskadeDato,
    )
}

private fun AnnenFraversArsak.toDTO(): AnnenFraversArsakDTO {
    return AnnenFraversArsakDTO(
        beskrivelse = beskrivelse,
        grunn = grunn.map { it.toDTO() },
    )
}

private fun AnnenFraverGrunn.toDTO(): AnnenFraverGrunnDTO {
    return when (this) {
        AnnenFraverGrunn.ABORT -> AnnenFraverGrunnDTO.ABORT
        AnnenFraverGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraverGrunnDTO.ARBEIDSRETTET_TILTAK
        AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID ->
            AnnenFraverGrunnDTO.BEHANDLING_FORHINDRER_ARBEID
        AnnenFraverGrunn.BEHANDLING_STERILISERING -> AnnenFraverGrunnDTO.BEHANDLING_STERILISERING
        AnnenFraverGrunn.DONOR -> AnnenFraverGrunnDTO.DONOR
        AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON -> AnnenFraverGrunnDTO.GODKJENT_HELSEINSTITUSJON
        AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND ->
            AnnenFraverGrunnDTO.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
        AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE ->
            AnnenFraverGrunnDTO.NODVENDIG_KONTROLLUNDENRSOKELSE
        AnnenFraverGrunn.SMITTEFARE -> AnnenFraverGrunnDTO.SMITTEFARE
        AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraverGrunnDTO.UFOR_GRUNNET_BARNLOSHET
    }
}

fun Diagnose.toDiagnoseDTO(): DiagnoseDTO {
    return DiagnoseDTO(
        kode = kode,
        system = getDiagnosesystem(system),
        tekst = getDiagnosetekst(this),
    )
}

fun Behandler.toBehandlerDTO(fullBehandler: Boolean = true): BehandlerDTO {
    return BehandlerDTO(
        fornavn = fornavn,
        mellomnavn = mellomnavn,
        etternavn = etternavn,
        aktoerId =
            if (fullBehandler) {
                aktoerId
            } else null,
        fnr =
            if (fullBehandler) {
                fnr
            } else null,
        her =
            if (fullBehandler) {
                her
            } else null,
        hpr =
            if (fullBehandler) {
                hpr
            } else null,
        tlf = tlf,
        adresse = adresse.toAdresseDTO(),
    )
}

private fun Adresse.toAdresseDTO(): AdresseDTO {
    return AdresseDTO(
        gate = gate,
        kommune = kommune,
        land = land,
        postboks = postboks,
        postnummer = postnummer,
    )
}

fun finnPeriodetype(periode: Periode, sykmeldingId: String): Periodetype =
    when {
        periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
        periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Periodetype.GRADERT
        periode.reisetilskudd -> Periodetype.REISETILSKUDD
        else ->
            throw RuntimeException(
                "Kunne ikke bestemme typen til periode: $periode for sykmelding med id $sykmeldingId"
            )
    }

private fun getDiagnosetekst(diagnose: Diagnose): String {
    if (!diagnose.tekst.isNullOrEmpty()) {
        return diagnose.tekst
    }
    return when (diagnose.system) {
        Diagnosekoder.ICD10_CODE -> (Diagnosekoder.icd10[diagnose.kode])?.text ?: "Ukjent"
        Diagnosekoder.ICPC2_CODE -> (Diagnosekoder.icpc2[diagnose.kode])?.text ?: "Ukjent"
        else -> "Ukjent"
    }
}

private fun getDiagnosesystem(system: String): String =
    when (system) {
        Diagnosekoder.ICD10_CODE -> "ICD-10"
        Diagnosekoder.ICPC2_CODE -> "ICPC-2"
        else -> "Ukjent"
    }
