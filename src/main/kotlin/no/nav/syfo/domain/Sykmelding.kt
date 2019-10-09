package no.nav.syfo.domain

import no.nav.syfo.aksessering.api.ArbeidsgiverDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallStatusDTO
import no.nav.syfo.aksessering.api.DiagnoseDTO
import no.nav.syfo.aksessering.api.FullstendigSykmeldingDTO
import no.nav.syfo.aksessering.api.GradertDTO
import no.nav.syfo.aksessering.api.MedisinskVurderingDTO
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.aksessering.api.RegelinfoDTO
import no.nav.syfo.aksessering.api.SkjermetSykmeldingDTO
import no.nav.syfo.aksessering.api.SykmeldingsperiodeDTO
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sm.Diagnosekoder.ICD10_CODE
import no.nav.syfo.sm.Diagnosekoder.ICPC2_CODE
import java.time.LocalDate
import java.time.LocalDateTime

data class Sykmelding(
    val id: String,
    val skjermesForPasient: Boolean,
    val mottattTidspunkt: LocalDateTime,
    val behandlingsutfall: Behandlingsutfall,
    val legekontorOrgnummer: String?,
    val legeNavn: String?,
    val arbeidsgiver: Arbeidsgiver?,
    val sykmeldingsperioder: List<Sykmeldingsperiode>,
    val bekreftetDato: LocalDateTime?,
    val medisinskVurdering: MedisinskVurdering
)

data class MedisinskVurdering(
    val hovedDiagnose: Diagnose?,
    val biDiagnoser: List<Diagnose>,
    val svangerskap: Boolean,
    val yrkesskade: Boolean,
    val yrkesskadedato: LocalDate?,
    val annenFravarsarsak: AnnenFravarsarsak?
)

data class AnnenFravarsarsak(
    val beskrivelse: String?,
    val grunn: Fravarsgrunn?
)

enum class Fravarsgrunn {
    GODKJENT_HELSEINSTITUSJON,
    BEHANDLING_FORHINDRER_ARBEID,
    ARBEIDSRETTET_TILTAK,
    MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND,
    NODVENDIG_KONTROLLUNDENRSOKELSE,
    SMITTEFARE,
    ABORT,
    UFOR_GRUNNET_BARNLOSHET,
    DONOR,
    BEHANDLING_STERILISERING
}

data class Behandlingsutfall(
    val ruleHits: List<Regelinfo>,
    val status: BehandlingsutfallStatus
)

data class Regelinfo(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String,
    val ruleStatus: BehandlingsutfallStatus?
)

enum class BehandlingsutfallStatus {
    OK, MANUAL_PROCESSING, INVALID
}

data class Arbeidsgiver(
    val navn: String,
    val stillingsprosent: Int?
)

data class Sykmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: Gradert?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: Periodetype
)

data class Gradert(
    val grad: Int,
    val reisetilskudd: Boolean
)

enum class Periodetype {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

data class Diagnose(
    val kode: String,
    val system: String
)

fun Sykmelding.toFullstendigDTO(): FullstendigSykmeldingDTO =
    FullstendigSykmeldingDTO(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        bekreftetDato = bekreftetDato,
        behandlingsutfall = behandlingsutfall.toDTO(),
        legekontorOrgnummer = legekontorOrgnummer,
        legeNavn = legeNavn,
        arbeidsgiver = arbeidsgiver?.toDTO(),
        sykmeldingsperioder = sykmeldingsperioder.map { it.toDTO() },
        medisinskVurdering = medisinskVurdering.toDTO()
    )

fun Sykmelding.toSkjermetDTO(): SkjermetSykmeldingDTO =
    SkjermetSykmeldingDTO(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        bekreftetDato = bekreftetDato,
        behandlingsutfall = behandlingsutfall.toDTO(),
        legekontorOrgnummer = legekontorOrgnummer,
        legeNavn = legeNavn,
        arbeidsgiver = arbeidsgiver?.toDTO(),
        sykmeldingsperioder = sykmeldingsperioder.map { it.toDTO() }
    )

fun MedisinskVurdering.toDTO(): MedisinskVurderingDTO =
        MedisinskVurderingDTO(
            hovedDiagnose = hovedDiagnose?.toDTO(),
            biDiagnoser = biDiagnoser.map { it.toDTO() }
        )

fun Behandlingsutfall.toDTO(): BehandlingsutfallDTO =
    BehandlingsutfallDTO(
        ruleHits = ruleHits.map { it.toDTO() },
        status = status.toDTO()
    )

fun Regelinfo.toDTO(): RegelinfoDTO =
    RegelinfoDTO(
        messageForSender = messageForSender,
        messageForUser = messageForUser,
        ruleName = ruleName,
        ruleStatus = ruleStatus
    )

fun BehandlingsutfallStatus.toDTO(): BehandlingsutfallStatusDTO =
    BehandlingsutfallStatusDTO.valueOf(this.name)

fun Arbeidsgiver.toDTO(): ArbeidsgiverDTO =
    ArbeidsgiverDTO(
        navn = navn,
        stillingsprosent = stillingsprosent
    )

fun Sykmeldingsperiode.toDTO(): SykmeldingsperiodeDTO =
    SykmeldingsperiodeDTO(
        fom = fom,
        tom = tom,
        gradert = gradert?.toDTO(),
        behandlingsdager = behandlingsdager,
        innspillTilArbeidsgiver = innspillTilArbeidsgiver,
        type = type.toDTO()
    )

fun Gradert.toDTO(): GradertDTO =
    GradertDTO(
        grad = grad,
        reisetilskudd = reisetilskudd
    )

fun Periodetype.toDTO(): PeriodetypeDTO =
    PeriodetypeDTO.valueOf(this.name)

fun Diagnose.toDTO(): DiagnoseDTO =
    DiagnoseDTO(
        diagnosekode = kode,
        diagnosesystem = getDiagnosesystem(system),
        diagnosetekst = getDiagnosetekst(this))

fun getDiagnosetekst(diagnose: Diagnose): String =
    when (diagnose.system) {
        ICD10_CODE ->
            (Diagnosekoder.icd10[diagnose.kode])?.text ?: "Ukjennt"
        ICPC2_CODE ->
            (Diagnosekoder.icpc2[diagnose.kode])?.text ?: "Ukjennt"
        else -> "Ukjennt"
    }

fun getDiagnosesystem(system: String): String =
        when (system) {
            ICD10_CODE -> "ICD-10"
            ICPC2_CODE -> "ICPC-2"
            else -> "Ukjennt"
        }
