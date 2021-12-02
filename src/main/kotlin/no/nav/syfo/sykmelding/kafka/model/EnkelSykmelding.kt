package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MerknadDTO
import no.nav.syfo.sykmelding.model.PrognoseDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import java.time.LocalDate
import java.time.OffsetDateTime

data class EnkelSykmelding(
    val id: String,
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val behandletTidspunkt: OffsetDateTime,
    val meldingTilArbeidsgiver: String?,
    val navnFastlege: String?,
    val tiltakArbeidsplassen: String?,
    val syketilfelleStartDato: LocalDate?,
    val behandler: BehandlerDTO,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val arbeidsgiver: ArbeidsgiverDTO,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val prognose: PrognoseDTO?,
    val egenmeldt: Boolean,
    val papirsykmelding: Boolean,
    val harRedusertArbeidsgiverperiode: Boolean,
    val merknader: List<MerknadDTO>?
)
