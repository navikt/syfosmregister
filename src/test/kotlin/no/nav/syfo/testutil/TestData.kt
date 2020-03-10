package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sykmelding.db.Adresse
import no.nav.syfo.sykmelding.db.Arbeidsgiver
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Behandler
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.HarArbeidsgiver
import no.nav.syfo.sykmelding.db.KontaktMedPasient
import no.nav.syfo.sykmelding.db.MedisinskVurdering
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.Sykmelding
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AnnenFraversArsakDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
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
            "",
            "",
            "")
}

fun getSykmeldingDto(skjermet: Boolean = false): SykmeldingDTO {
    return SykmeldingDTO(
            id = "1",
            utdypendeOpplysninger = emptyMap(),
            kontaktMedPasient = KontaktMedPasientDTO(null, null),
            sykmeldingsperioder = getPerioder(),
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
            andreTiltak = null
    )
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

fun getSykmeldingerDBmodel(skjermet: Boolean = false): SykmeldingDbModel {
    val sykmeldingDbModel = SykmeldingDbModel(
            id = "123",
            behandlingsutfall = ValidationResult(Status.OK, emptyList()),
            mottattTidspunkt = LocalDateTime.now(),
            status = StatusDbModel(
                    statusEvent = "APEN",
                    arbeidsgiver = null,
                    statusTimestamp = LocalDateTime.now()
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
                            hovedDiagnose = Diagnose("system", "kode", "tekst"),
                            biDiagnoser = emptyList(),
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
                    perioder = emptyList(),
                    signaturDato = LocalDateTime.now()
            ))
    return sykmeldingDbModel
}
