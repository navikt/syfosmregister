package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.domain.Arbeidsgiver
import no.nav.syfo.domain.Behandlingsutfall
import no.nav.syfo.domain.BehandlingsutfallStatus
import no.nav.syfo.domain.Gradert
import no.nav.syfo.domain.Periodetype
import no.nav.syfo.domain.Regelinfo
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.domain.Sykmeldingsperiode
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.Periode
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.syfo.model.Arbeidsgiver as ModelArbeidsgiver
import no.nav.syfo.model.Gradert as ModelGradert

data class Brukersykmelding(
    val id: String,
    val mottakId: String,
    val msgId: String,
    val bekreftetDato: LocalDateTime?,
    val mottattTidspunkt: LocalDateTime,
    val behandlingsutfall: Brukerbehandlingsutfall,
    val legekontorOrgnummer: String?,
    val legeNavn: String?,
    val arbeidsgiver: Brukerarbeidsgiver?,
    val sykmeldingsperioder: List<Brukersykmeldingsperiode>
)

fun Brukersykmelding.toSykmelding(): Sykmelding =
    Sykmelding(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        bekreftetDato = bekreftetDato,
        behandlingsutfall = behandlingsutfall.toSykmelding(),
        legekontorOrgnummer = legekontorOrgnummer,
        legeNavn = legeNavn,
        arbeidsgiver = arbeidsgiver?.toSykmelding(),
        sykmeldingsperioder = sykmeldingsperioder.map { it.toDTO() }
    )

data class Brukerbehandlingsutfall(
    val ruleHits: List<Brukerregelinfo>,
    val status: BrukerbehandlingsutfallStatus
)

fun Brukerbehandlingsutfall.toSykmelding(): Behandlingsutfall =
    Behandlingsutfall(
        ruleHits = ruleHits.map { it.toSykmelding() },
        status = status.toSykmelding()
    )

data class Brukerregelinfo(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String
)

fun Brukerregelinfo.toSykmelding(): Regelinfo =
    Regelinfo(
        messageForSender = messageForSender,
        messageForUser = messageForUser,
        ruleName = ruleName
    )

enum class BrukerbehandlingsutfallStatus {
    OK, MANUAL_PROCESSING, INVALID
}

fun BrukerbehandlingsutfallStatus.toSykmelding(): BehandlingsutfallStatus =
    BehandlingsutfallStatus.valueOf(this.name)

data class Brukerarbeidsgiver(
    val navn: String,
    val stillingsprosent: Int?
)

fun Brukerarbeidsgiver.toSykmelding(): Arbeidsgiver =
    Arbeidsgiver(
        navn = navn,
        stillingsprosent = stillingsprosent
    )

data class Brukersykmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: Brukergradert?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: Brukerperiodetype
)

fun Brukersykmeldingsperiode.toDTO(): Sykmeldingsperiode =
    Sykmeldingsperiode(
        fom = fom,
        tom = tom,
        gradert = gradert?.toSykmelding(),
        behandlingsdager = behandlingsdager,
        innspillTilArbeidsgiver = innspillTilArbeidsgiver,
        type = type.toSykmelding()
    )

data class Brukergradert(
    val grad: Int,
    val reisetilskudd: Boolean
)

fun Brukergradert.toSykmelding(): Gradert =
    Gradert(
        grad = grad,
        reisetilskudd = reisetilskudd
    )

enum class Brukerperiodetype {
    AKTIVITET_IKKE_MULIG,
    AVVENTENDE,
    BEHANDLINGSDAGER,
    GRADERT,
    REISETILSKUDD,
}

fun Brukerperiodetype.toSykmelding(): Periodetype =
    Periodetype.valueOf(this.name)

fun brukersykmeldingFromResultSet(resultSet: ResultSet): Brukersykmelding =
    Brukersykmelding(
        id = resultSet.getString("id").trim(),
        mottakId = resultSet.getString("mottak_id").trim(),
        msgId = resultSet.getString("msg_id").trim(),
        mottattTidspunkt = resultSet.getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        bekreftetDato = resultSet.getTimestamp("bekreftet_dato")?.toLocalDateTime(),
        behandlingsutfall = objectMapper.readValue(resultSet.getString("behandlings_utfall")),
        legekontorOrgnummer = resultSet.getString("legekontor_org_nr").trim(),
        legeNavn = getLegenavn(resultSet),
        arbeidsgiver = arbeidsgiverModelTilBrukerarbeidsgiver(
            objectMapper.readValue(
                resultSet.getString(
                    "arbeidsgiver"
                )
            )
        ),
        sykmeldingsperioder = getSykmeldingsperioder(resultSet).map {
            periodeTilBrukersykmeldingsperiode(
                it
            )
        }
    )

fun arbeidsgiverModelTilBrukerarbeidsgiver(arbeidsgiver: ModelArbeidsgiver): Brukerarbeidsgiver? {
    return if (arbeidsgiver.harArbeidsgiver != HarArbeidsgiver.INGEN_ARBEIDSGIVER) {
        Brukerarbeidsgiver(
            navn = arbeidsgiver.navn ?: "",
            stillingsprosent = arbeidsgiver.stillingsprosent
        )
    } else null
}

fun periodeTilBrukersykmeldingsperiode(periode: Periode): Brukersykmeldingsperiode =
    Brukersykmeldingsperiode(
        fom = periode.fom,
        tom = periode.tom,
        gradert = modelGradertTilBrukergradert(periode.gradert),
        behandlingsdager = periode.behandlingsdager,
        innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver,
        type = finnPeriodetype(periode)
    )

fun modelGradertTilBrukergradert(gradert: ModelGradert?): Brukergradert? =
    gradert?.let { Brukergradert(grad = it.grad, reisetilskudd = it.reisetilskudd) }

fun finnPeriodetype(periode: Periode): Brukerperiodetype =
    when {
        periode.aktivitetIkkeMulig != null -> Brukerperiodetype.AKTIVITET_IKKE_MULIG
        periode.avventendeInnspillTilArbeidsgiver != null -> Brukerperiodetype.AVVENTENDE
        periode.behandlingsdager != null -> Brukerperiodetype.BEHANDLINGSDAGER
        periode.gradert != null -> Brukerperiodetype.GRADERT
        periode.reisetilskudd -> Brukerperiodetype.REISETILSKUDD
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
    }

fun getSykmeldingsperioder(resultSet: ResultSet): List<Periode> =
    objectMapper.readValue(resultSet.getString("perioder"))

fun getLegenavn(resultSet: ResultSet): String? {
    val fornavn = when (val value = resultSet.getString("lege_fornavn")) {
        null -> ""
        else -> value.plus(" ")
    }
    val mellomnavn = when (val value = resultSet.getString("lege_mellomnavn")) {
        null -> ""
        else -> value.plus(" ")
    }
    val etternavn = when (val value = resultSet.getString("lege_etternavn")) {
        null -> ""
        else -> value
    }
    val navn = "$fornavn$mellomnavn$etternavn"

    return if (navn.isBlank()) null else navn
}
