package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.ArbeidsgiverDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallStatusDTO
import no.nav.syfo.aksessering.api.DiagnoseDTO
import no.nav.syfo.aksessering.api.FullstendigSykmeldingDTO
import no.nav.syfo.aksessering.api.InternalSykmeldingDTO
import no.nav.syfo.aksessering.api.MedisinskVurderingDTO
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.aksessering.api.SkjermetSykmeldingDTO
import no.nav.syfo.aksessering.api.SykmeldingDTO
import no.nav.syfo.aksessering.api.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusDTO

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

fun getSykmelding(skjermet: Boolean = false): SykmeldingDTO {
    return when {
        skjermet -> getSkjermetSykmeldign()
        else -> getFullstendingSykmelding()
    }
}

fun getInternalSykmelding(): InternalSykmeldingDTO {
    return InternalSykmeldingDTO(
            id = "1",
            arbeidsgiver = getArbeidsgiver(),
            sykmeldingStatus = getSykmeldingStatus(),
            sykmeldingsperioder = getPerioder(),
            legeNavn = "legenavn",
            legekontorOrgnummer = "123456789",
            bekreftetDato = LocalDateTime.now(),
            behandlingsutfall = getBehandlingsutfall(),
            mottattTidspunkt = LocalDateTime.now(),
            medisinskVurdering = getMedisinskVurdering(),
            skjermesForPasient = false
    )
}

fun getFullstendingSykmelding(): FullstendigSykmeldingDTO {
    return FullstendigSykmeldingDTO(
            id = "1",
            arbeidsgiver = getArbeidsgiver(),
            sykmeldingStatus = getSykmeldingStatus(),
            sykmeldingsperioder = getPerioder(),
            legeNavn = "legenavn",
            legekontorOrgnummer = "123456789",
            bekreftetDato = LocalDateTime.now(),
            behandlingsutfall = getBehandlingsutfall(),
            mottattTidspunkt = LocalDateTime.now(),
            medisinskVurdering = getMedisinskVurdering()
    )
}

fun getMedisinskVurdering(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
            hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
            biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose"))
    )
}

fun getSkjermetSykmeldign(): SkjermetSykmeldingDTO {
    return SkjermetSykmeldingDTO(
            id = "1",
            arbeidsgiver = getArbeidsgiver(),
            mottattTidspunkt = LocalDateTime.now(),
            behandlingsutfall = getBehandlingsutfall(),
            bekreftetDato = LocalDateTime.now(),
            legekontorOrgnummer = "123456789",
            legeNavn = "Lege Lege",
            sykmeldingsperioder = getPerioder(),
            sykmeldingStatus = getSykmeldingStatus()
    )
}

fun getSykmeldingStatus(): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(
            timestamp = LocalDateTime.now(),
            statusEvent = StatusEventDTO.SENDT,
            arbeidsgiver = getArbeidsgiverStatus(),
            sporsmalOgSvarListe = getSporsmalOgSvarListe())
}

fun getSporsmalOgSvarListe(): List<SporsmalOgSvarDTO>? {
    return listOf(
            SporsmalOgSvarDTO(tekst = "arbeidsgiver",
                    svar = "EN_ARBEIDSGIVER",
                    svartype = SvartypeDTO.ARBEIDSSITUASJON,
                    shortName = ShortNameDTO.ARBEIDSSITUASJON)
    )
}

fun getArbeidsgiverStatus(): ArbeidsgiverStatusDTO? {
    return ArbeidsgiverStatusDTO("123456789", null, "orgnavn")
}

fun getPerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), null, null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG))
}

fun getBehandlingsutfall(): BehandlingsutfallDTO {
    return BehandlingsutfallDTO(emptyList(), BehandlingsutfallStatusDTO.OK)
}

fun getArbeidsgiver(): ArbeidsgiverDTO? {
    return ArbeidsgiverDTO("arbeidsgiver", 100)
}
