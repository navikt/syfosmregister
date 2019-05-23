package no.nav.syfo.domain

import no.nav.syfo.aksessering.api.ArbeidsgiverDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallDTO
import no.nav.syfo.aksessering.api.BehandlingsutfallStatusDTO
import no.nav.syfo.aksessering.api.GradertDTO
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.aksessering.api.RegelinfoDTO
import no.nav.syfo.aksessering.api.SykmeldingDTO
import no.nav.syfo.aksessering.api.SykmeldingsperiodeDTO
import java.time.LocalDate
import java.time.LocalDateTime

data class Sykmelding(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: Behandlingsutfall,
    val legekontorOrgnummer: String,
    val legeNavn: String?,
    val arbeidsgiver: Arbeidsgiver?,
    val sykmeldingsperioder: List<Sykmeldingsperiode>
)

data class Behandlingsutfall(
    val ruleHits: List<Regelinfo>,
    val status: BehandlingsutfallStatus
)

data class Regelinfo(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String
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

fun Sykmelding.toDTO(): SykmeldingDTO =
    SykmeldingDTO(
        id = id,
        bekreftetDato = bekreftetDato,
        behandlingsutfall = behandlingsutfall.toDTO(),
        legekontorOrgnummer = legekontorOrgnummer,
        legeNavn = legeNavn,
        arbeidsgiver = arbeidsgiver?.toDTO(),
        sykmeldingsperioder = sykmeldingsperioder.map { it.toDTO() }
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
        ruleName = ruleName
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
