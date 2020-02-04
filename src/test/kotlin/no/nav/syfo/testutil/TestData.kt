package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.sykmelding.internal.model.AdresseDTO
import no.nav.syfo.sykmelding.internal.model.BehandlerDTO
import no.nav.syfo.sykmelding.internal.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.internal.model.DiagnoseDTO
import no.nav.syfo.sykmelding.internal.model.InternalSykmeldingDTO
import no.nav.syfo.sykmelding.internal.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.internal.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.internal.model.RegelStatusDTO
import no.nav.syfo.sykmelding.internal.model.SykmeldingsperiodeDTO

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
            "",
            "")
}

fun getInternalSykmelding(skjermet: Boolean = false): InternalSykmeldingDTO {
    return InternalSykmeldingDTO(
            id = "1",
            utdypendeOpplysninger = emptyMap(),
            kontaktMedPasient = KontaktMedPasientDTO(null, null),
            sykmeldingsperioder = getPerioder(),
            sykmeldingStatus = no.nav.syfo.sykmelding.internal.model.SykmeldingStatusDTO("APEN", ZonedDateTime.now(ZoneOffset.UTC), null),
            behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
            medisinskVurdering = getMedisinskVurdering(),
            behandler = BehandlerDTO(
                    "fornavn", null, "etternavn",
                    "123", "01234567891", null, null,
                    AdresseDTO(null, null, null, null, null), null),
            behandletTidspunkt = ZonedDateTime.now(ZoneOffset.UTC),
            mottattTidspunkt = ZonedDateTime.now(ZoneOffset.UTC),
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
            biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose"))
    )
}

fun getPerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), null, null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false))
}
