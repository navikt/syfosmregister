package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.sykmelding.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.sykmelding.model.getUtcTime
import no.nav.syfo.sykmelding.model.toArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.toBehandlerDTO
import no.nav.syfo.sykmelding.model.toKontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.toPrognoseDTO
import no.nav.syfo.sykmelding.model.toSykmeldingsperiodeDTO

fun SykmeldingDbModelUtenBehandlingsutfall.toEnkelSykmelding(): EnkelSykmelding {
    return EnkelSykmelding(
            id = id,
            mottattTidspunkt = getUtcTime(mottattTidspunkt),
            legekontorOrgnummer = legekontorOrgNr,
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
            egenmeldt = sykmeldingsDokument.avsenderSystem.navn == "Egenmeldt",
            papirsykmelding = sykmeldingsDokument.avsenderSystem.navn == "Papirsykmelding",
            harRedusertArbeidsgiverperiode = sykmeldingsDokument.medisinskVurdering.getHarRedusertArbeidsgiverperiode()
    )
}
