package no.nav.syfo.sykmelding.internal.model

import java.time.LocalDate
import java.time.LocalDateTime

data class InternalSykmeldingDTO(
    val id: String,
    val mottattTidspunkt: LocalDateTime,
    val behandlingsutfall: BehandlingsutfallDTO,
    val legekontorOrgnummer: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val sykmeldingStatus: SykmeldingStatusDTO,
    val medisinskVurdering: MedisinskVurderingDTO,
    val skjermesForPasient: Boolean,
    val prognose: PrognoseDTO?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvarDTO>>,
    val tiltakArbeidsplassen: String?,
    val tiltakNAV: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNavDTO?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val behandletTidspunkt: LocalDateTime,
    val behandler: BehandlerDTO,
    val syketilfelleStartDato: LocalDate?,
    val navnFastlege: String?
)
