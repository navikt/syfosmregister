package no.nav.syfo.sykmeldingstatus.kafka.model

import no.nav.syfo.sykmelding.db.SporsmalSvar
import no.nav.syfo.sykmelding.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.sykmelding.model.SporsmalSvarDTO
import no.nav.syfo.sykmelding.model.SvarRestriksjonDTO
import no.nav.syfo.sykmelding.model.getHarRedusertArbeidsgiverperiode
import no.nav.syfo.sykmelding.model.getUtcTime
import no.nav.syfo.sykmelding.model.toArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.toBehandlerDTO
import no.nav.syfo.sykmelding.model.toKontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.toPrognoseDTO
import no.nav.syfo.sykmelding.model.toSporsmalSvarDTO
import no.nav.syfo.sykmelding.model.toSykmeldingsperiodeDTO

fun SykmeldingDbModelUtenBehandlingsutfall.toSendtSykmelding(): SendtSykmelding {
    return SendtSykmelding(
            id = id,
            mottattTidspunkt = getUtcTime(mottattTidspunkt),
            legekontorOrgnr = legekontorOrgNr,
            behandletTidspunkt = getUtcTime(sykmeldingsDokument.behandletTidspunkt),
            meldingTilArbeidsgiver = sykmeldingsDokument.meldingTilArbeidsgiver,
            navnFastlege = sykmeldingsDokument.navnFastlege,
            tiltakArbeidsplassen = sykmeldingsDokument.tiltakArbeidsplassen,
            syketilfelleStartDato = sykmeldingsDokument.syketilfelleStartDato,
            behandler = sykmeldingsDokument.behandler.toBehandlerDTO(),
            sykmeldingsperioder = sykmeldingsDokument.perioder.map { it.toSykmeldingsperiodeDTO() },
            arbeidsgiver = sykmeldingsDokument.arbeidsgiver.toArbeidsgiverDTO(),
            kontaktMedPasient = sykmeldingsDokument.kontaktMedPasient.toKontaktMedPasientDTO(),
            prognose = sykmeldingsDokument.prognose?.toPrognoseDTO(),
            utdypendeOpplysninger = toUtdypendeOpplysninger(sykmeldingsDokument.utdypendeOpplysninger),
            egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
            papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
            harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode()
    )
}

private fun toUtdypendeOpplysninger(utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>): Map<String, Map<String, SporsmalSvarDTO>> {
    return utdypendeOpplysninger.mapValues {
        it.value.mapValues { entry -> entry.value.toSporsmalSvarDTO() }
                .filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_ARBEIDSGIVER) }
    }
}
