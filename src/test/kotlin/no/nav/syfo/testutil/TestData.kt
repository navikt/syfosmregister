package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.db.Adresse
import no.nav.syfo.sykmelding.db.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.db.Arbeidsgiver
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Behandler
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.Gradert
import no.nav.syfo.sykmelding.db.HarArbeidsgiver
import no.nav.syfo.sykmelding.db.KontaktMedPasient
import no.nav.syfo.sykmelding.db.MedisinskArsak
import no.nav.syfo.sykmelding.db.MedisinskVurdering
import no.nav.syfo.sykmelding.db.MeldingTilNAV
import no.nav.syfo.sykmelding.db.Periode
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.Sykmelding
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AnnenFraversArsakDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
import no.nav.syfo.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO

fun getVaultSecrets(): VaultSecrets {
    return VaultSecrets(
            "",
            "",
            "",
            "",
            "",
            "",
            ""
    )
}

fun getSykmeldingDto(skjermet: Boolean = false, perioder: List<SykmeldingsperiodeDTO> = getPerioder()): SykmeldingDTO {
    return SykmeldingDTO(
            id = "1",
            utdypendeOpplysninger = emptyMap(),
            kontaktMedPasient = KontaktMedPasientDTO(null, null),
            sykmeldingsperioder = perioder,
            sykmeldingStatus = no.nav.syfo.sykmelding.model.SykmeldingStatusDTO("APEN", OffsetDateTime.now(ZoneOffset.UTC), null, emptyList()),
            behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
            medisinskVurdering = getMedisinskVurdering(),
            behandler = BehandlerDTO(
                    "fornavn", null, "etternavn",
                    "123", "01234567891", null, null,
                    AdresseDTO(null, null, null, null, null), null),
            behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
            mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
            skjermesForPasient = false,
            meldingTilNAV = null,
            prognose = null,
            arbeidsgiver = null,
            tiltakNAV = null,
            syketilfelleStartDato = null,
            tiltakArbeidsplassen = null,
            navnFastlege = null,
            meldingTilArbeidsgiver = null,
            legekontorOrgnummer = null,
            andreTiltak = null,
            egenmeldt = false,
            harRedusertArbeidsgiverperiode = false,
            papirsykmelding = false,
            merknader = null)
}

fun getMedisinskVurdering(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
            hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
            biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose")),
            annenFraversArsak = AnnenFraversArsakDTO("", emptyList()),
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null
    )
}

fun getPerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), null, null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false))
}

fun getGradertePerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), GradertDTO(50, false), null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false))
}

fun getSykmeldingerDBmodel(skjermet: Boolean = false, perioder: List<Periode> = emptyList()): SykmeldingDbModel {
    return SykmeldingDbModel(
            id = "123",
            behandlingsutfall = ValidationResult(Status.OK, emptyList()),
            mottattTidspunkt = OffsetDateTime.now(),
            status = StatusDbModel(
                    statusEvent = "APEN",
                    arbeidsgiver = null,
                    statusTimestamp = OffsetDateTime.now()
            ),
            legekontorOrgNr = "123456789",
            sykmeldingsDokument = Sykmelding(
                    id = "123",
                    arbeidsgiver = Arbeidsgiver(
                            harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                            navn = "navn",
                            stillingsprosent = null,
                            yrkesbetegnelse = null),
                    medisinskVurdering = MedisinskVurdering(
                            hovedDiagnose = Diagnose(Diagnosekoder.ICPC2_CODE, "L87", "tekst"),
                            biDiagnoser = emptyList(),
                            yrkesskade = false,
                            svangerskap = false,
                            annenFraversArsak = null,
                            yrkesskadeDato = null
                    ),
                    andreTiltak = "Andre tiltak",
                    meldingTilArbeidsgiver = null,
                    navnFastlege = null,
                    tiltakArbeidsplassen = null,
                    syketilfelleStartDato = null,
                    tiltakNAV = "Tiltak NAV",
                    prognose = null,
                    meldingTilNAV = MeldingTilNAV(true, "Masse bistand"),
                    skjermesForPasient = skjermet,
                    behandletTidspunkt = LocalDateTime.now(),
                    behandler = Behandler(
                            "fornavn",
                            null,
                            "etternavn",
                            "aktorId",
                            "01234567891",
                            null,
                            null,
                            Adresse(null, null, null, null, null),
                            null),
                    kontaktMedPasient = KontaktMedPasient(
                            LocalDate.now(),
                            "Begrunnelse"
                    ),
                    utdypendeOpplysninger = emptyMap(),
                    msgId = "msgid",
                    pasientAktoerId = "aktorId",
                    avsenderSystem = AvsenderSystem("Navn", "verjosn"),
                    perioder = perioder,
                    signaturDato = LocalDateTime.now()
            ),
            merknader = null)
}

fun getPeriode(fom: LocalDate, tom: LocalDate, gradert: Gradert? = null): Periode {
    return Periode(
            fom = fom,
            tom = tom,
            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = gradert,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
    )
}

fun getSykmeldingerDBmodelEgenmeldt(hovediagnosekode: String = "kode", bidiagnoser: List<Diagnose> = emptyList(), avsenderSystem: AvsenderSystem = AvsenderSystem("Nobody", "versjon"), perioder: List<Periode> = emptyList()): SykmeldingDbModel {
    return SykmeldingDbModel(
            id = "123",
            behandlingsutfall = ValidationResult(Status.OK, emptyList()),
            mottattTidspunkt = OffsetDateTime.now(),
            status = StatusDbModel(
                    statusEvent = "APEN",
                    arbeidsgiver = null,
                    statusTimestamp = OffsetDateTime.now()
            ),
            legekontorOrgNr = "123456789",
            sykmeldingsDokument = Sykmelding(
                    id = "123",
                    arbeidsgiver = Arbeidsgiver(
                            harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                            navn = "navn",
                            stillingsprosent = null,
                            yrkesbetegnelse = null),
                    medisinskVurdering = MedisinskVurdering(
                            hovedDiagnose = Diagnose("system", hovediagnosekode, "tekst"),
                            biDiagnoser = bidiagnoser,
                            yrkesskade = false,
                            svangerskap = false,
                            annenFraversArsak = null,
                            yrkesskadeDato = null
                    ),
                    andreTiltak = null,
                    meldingTilArbeidsgiver = null,
                    navnFastlege = null,
                    tiltakArbeidsplassen = null,
                    syketilfelleStartDato = null,
                    tiltakNAV = null,
                    prognose = null,
                    meldingTilNAV = null,
                    skjermesForPasient = false,
                    behandletTidspunkt = LocalDateTime.now(),
                    behandler = Behandler(
                            "fornavn",
                            null,
                            "etternavn",
                            "aktorId",
                            "01234567891",
                            null,
                            null,
                            Adresse(null, null, null, null, null),
                            null),
                    kontaktMedPasient = KontaktMedPasient(
                            LocalDate.now(),
                            "Begrunnelse"
                    ),
                    utdypendeOpplysninger = emptyMap(),
                    msgId = "msgid",
                    pasientAktoerId = "aktorId",
                    avsenderSystem = avsenderSystem,
                    perioder = perioder,
                    signaturDato = LocalDateTime.now()
            ),
            merknader = null)
}
