package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.domain.Arbeidsgiver
import no.nav.syfo.domain.Behandlingsutfall
import no.nav.syfo.domain.BehandlingsutfallStatus
import no.nav.syfo.domain.Gradert
import no.nav.syfo.domain.Periodetype
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.domain.Sykmeldingsperiode
import no.nav.syfo.model.Arbeidsgiver as ModelArbeidsgiver
import no.nav.syfo.model.Gradert as ModelGradert
import no.nav.syfo.model.HarArbeidsgiver as ModelHarArbeidsgiver
import no.nav.syfo.model.Periode as ModelPeriode
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingStatus

fun DatabaseInterface.hentSykmeldinger(fnr: String): List<Sykmelding> =
        connection.use { connection ->
            val sykmeldingerMedSisteStatus = connection.hentSykmeldingerMedSisteStatus(fnr)
            return sykmeldingerMedSisteStatus.map {
                when {
                    it.sykmeldingStatus.statusEvent == StatusEvent.BEKREFTET ->
                        it.copy(sykmeldingStatus = connection.hentStatusMedSporsmalOgSvar(it.id, it.sykmeldingStatus, false))
                    it.sykmeldingStatus.statusEvent == StatusEvent.SENDT ->
                        it.copy(sykmeldingStatus = connection.hentStatusMedSporsmalOgSvar(it.id, it.sykmeldingStatus, true))
                    else -> it
                }
            }
        }

private fun Connection.hentSykmeldingerMedSisteStatus(fnr: String): List<Sykmelding> =
        this.prepareStatement(
                """
                SELECT opplysninger.id,
                       mottatt_tidspunkt,
                       behandlingsutfall,
                       legekontor_org_nr,
                       pasient_fnr,
                       status.event,
                       status.timestamp,
                       jsonb_extract_path(dokument.sykmelding, 'skjermesForPasient')::JSONB AS skjermes_for_pasient,
                       dokument.sykmelding -> 'behandler' ->> 'fornavn'                     AS lege_fornavn,
                       dokument.sykmelding -> 'behandler' ->> 'mellomnavn'                  AS lege_mellomnavn,
                       dokument.sykmelding -> 'behandler' ->> 'etternavn'                   AS lege_etternavn,
                       jsonb_extract_path(dokument.sykmelding, 'arbeidsgiver')::JSONB       AS arbeidsgiver,
                       jsonb_extract_path(dokument.sykmelding, 'perioder')::JSONB           AS perioder,
                       jsonb_extract_path(dokument.sykmelding, 'medisinskVurdering')::JSONB AS medisinsk_vurdering
                FROM sykmeldingsopplysninger AS opplysninger
                         INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                         INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                         LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                       status.timestamp = (SELECT timestamp
                                                                                                 FROM sykmeldingstatus
                                                                                                 WHERE sykmelding_id = opplysninger.id
                                                                                                 ORDER BY timestamp DESC
                                                                                                 LIMIT 1)
                WHERE pasient_fnr = ?
                  AND NOT exists(SELECT 1 FROM sykmeldingstatus WHERE event = 'SLETTET' AND sykmelding_id = opplysninger.id);
            """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toSykmelding() }
        }

internal fun Connection.hentStatusMedSporsmalOgSvar(sykmeldingId: String, sykmeldingStatus: SykmeldingStatus, skalHenteArbeidsgiver: Boolean): SykmeldingStatus {
    if (skalHenteArbeidsgiver) {
        return sykmeldingStatus.copy(arbeidsgiver = this.hentArbeidsgiverStatus(sykmeldingId).firstOrNull(), sporsmalListe = this.hentSporsmalOgSvar(sykmeldingId))
    }
    return sykmeldingStatus.copy(sporsmalListe = this.hentSporsmalOgSvar(sykmeldingId))
}

fun Connection.hentSporsmalOgSvar(sykmeldingId: String): List<Sporsmal> =
        this.prepareStatement(
                """
                SELECT sporsmal.shortname,
                       sporsmal.tekst,
                       svar.sporsmal_id,
                       svar.svar,
                       svar.svartype,
                       svar.sykmelding_id
                FROM svar
                         INNER JOIN sporsmal
                                    ON sporsmal.id = svar.sporsmal_id
                WHERE svar.sykmelding_id = ?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilSporsmal() }
        }

private fun Connection.hentArbeidsgiverStatus(sykmeldingId: String): List<ArbeidsgiverStatus> =
        this.prepareStatement(
                """
                 SELECT orgnummer,
                        juridisk_orgnummer,
                        navn,
                        sykmelding_id
                   FROM arbeidsgiver
                  WHERE sykmelding_id = ?
            """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilArbeidsgiverStatus() }
        }

fun DatabaseInterface.erEier(sykmeldingsid: String, fnr: String): Boolean =
        connection.use { connection ->
            connection.prepareStatement(
                    """
           SELECT 1 FROM SYKMELDINGSOPPLYSNINGER WHERE id=? AND pasient_fnr=?
            """
            ).use {
                it.setString(1, sykmeldingsid)
                it.setString(2, fnr)
                it.executeQuery().next()
            }
        }

fun ResultSet.toSykmelding(): Sykmelding {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return Sykmelding(
            id = getString("id").trim(),
            skjermesForPasient = getBoolean("skjermes_for_pasient"),
            mottattTidspunkt = mottattTidspunkt,
            behandlingsutfall = filterBehandlingsUtfall(objectMapper.readValue(getString("behandlingsutfall"))),
            legekontorOrgnummer = getString("legekontor_org_nr")?.trim(),
            legeNavn = getLegenavn(this),
            arbeidsgiver = arbeidsgiverModelTilSykmeldingarbeidsgiver(
                    objectMapper.readValue(
                            getString(
                                    "arbeidsgiver"
                            )
                    )
            ),
            sykmeldingsperioder = getSykmeldingsperioder(this).map {
                periodeTilBrukersykmeldingsperiode(it)
            },
            medisinskVurdering = objectMapper.readValue(getString("medisinsk_vurdering")),
            sykmeldingStatus = getSykmeldingStatus(mottattTidspunkt)
    )
}

private fun ResultSet.getSykmeldingStatus(mottattTidspunkt: OffsetDateTime): SykmeldingStatus {
    return when (getString("event")) {
        null -> SykmeldingStatus(mottattTidspunkt, StatusEvent.APEN, null, null)
        else -> SykmeldingStatus(timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC),
                statusEvent = tilStatusEvent(getString("event")),
                arbeidsgiver = null,
                sporsmalListe = null)
    }
}

fun filterBehandlingsUtfall(behandlingsutfall: Behandlingsutfall): Behandlingsutfall {
    if (behandlingsutfall.status == BehandlingsutfallStatus.MANUAL_PROCESSING) {
        return Behandlingsutfall(emptyList(), BehandlingsutfallStatus.MANUAL_PROCESSING)
    }
    return behandlingsutfall
}

fun arbeidsgiverModelTilSykmeldingarbeidsgiver(arbeidsgiver: ModelArbeidsgiver): Arbeidsgiver? {
    return if (arbeidsgiver.harArbeidsgiver != ModelHarArbeidsgiver.INGEN_ARBEIDSGIVER) {
        Arbeidsgiver(
                navn = arbeidsgiver.navn ?: "",
                stillingsprosent = arbeidsgiver.stillingsprosent
        )
    } else null
}

fun periodeTilBrukersykmeldingsperiode(periode: ModelPeriode): Sykmeldingsperiode =
        Sykmeldingsperiode(
                fom = periode.fom,
                tom = periode.tom,
                gradert = modelGradertTilGradert(periode.gradert),
                behandlingsdager = periode.behandlingsdager,
                innspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver,
                type = finnPeriodetype(periode)
        )

fun modelGradertTilGradert(gradert: ModelGradert?): Gradert? =
        gradert?.let { Gradert(grad = it.grad, reisetilskudd = it.reisetilskudd) }

fun finnPeriodetype(periode: ModelPeriode): Periodetype =
        when {
            periode.aktivitetIkkeMulig != null -> Periodetype.AKTIVITET_IKKE_MULIG
            periode.avventendeInnspillTilArbeidsgiver != null -> Periodetype.AVVENTENDE
            periode.behandlingsdager != null -> Periodetype.BEHANDLINGSDAGER
            periode.gradert != null -> Periodetype.GRADERT
            periode.reisetilskudd -> Periodetype.REISETILSKUDD
            else -> throw RuntimeException("Kunne ikke bestemme typen til periode: $periode")
        }

fun getSykmeldingsperioder(resultSet: ResultSet): List<ModelPeriode> =
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

fun ResultSet.tilSporsmal(): Sporsmal =
        Sporsmal(
                tekst = getString("tekst"),
                shortName = tilShortName(getString("shortname")),
                svar = tilSvar()
        )

fun ResultSet.tilSvar(): Svar =
        Svar(
                sykmeldingId = getString("sykmelding_id"),
                sporsmalId = getInt("sporsmal_id"),
                svartype = tilSvartype(getString("svartype")),
                svar = getString("svar")
        )

fun ResultSet.tilArbeidsgiverStatus(): ArbeidsgiverStatus =
        ArbeidsgiverStatus(
                sykmeldingId = getString("sykmelding_id"),
                orgnummer = getString("orgnummer"),
                juridiskOrgnummer = getString("juridisk_orgnummer"),
                orgnavn = getString("navn")
        )

fun tilStatusEvent(status: String): StatusEvent {
    return when (status) {
        "BEKREFTET" -> StatusEvent.BEKREFTET
        "APEN" -> StatusEvent.APEN
        "SENDT" -> StatusEvent.SENDT
        "AVBRUTT" -> StatusEvent.AVBRUTT
        "UTGATT" -> StatusEvent.UTGATT
        else -> throw IllegalStateException("Sykmeldingen har ukjent status eller er slettet, skal ikke kunne skje")
    }
}

private fun tilShortName(shortname: String): ShortName {
    return when (shortname) {
        "ARBEIDSSITUASJON" -> ShortName.ARBEIDSSITUASJON
        "FORSIKRING" -> ShortName.FORSIKRING
        "FRAVAER" -> ShortName.FRAVAER
        "PERIODE" -> ShortName.PERIODE
        "NY_NARMESTE_LEDER" -> ShortName.NY_NARMESTE_LEDER
        else -> throw IllegalStateException("Sykmeldingen har en ukjent spørsmålskode, skal ikke kunne skje")
    }
}

private fun tilSvartype(svartype: String): Svartype {
    return when (svartype) {
        "ARBEIDSSITUASJON" -> Svartype.ARBEIDSSITUASJON
        "PERIODER" -> Svartype.PERIODER
        "JA_NEI" -> Svartype.JA_NEI
        else -> throw IllegalStateException("Sykmeldingen har en ukjent svartype, skal ikke kunne skje")
    }
}
