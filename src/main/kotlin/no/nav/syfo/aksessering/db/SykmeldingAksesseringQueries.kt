package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.domain.Arbeidsgiver
import no.nav.syfo.domain.Gradert
import no.nav.syfo.domain.Periodetype
import no.nav.syfo.domain.Sykmelding
import no.nav.syfo.domain.Sykmeldingsperiode
import no.nav.syfo.model.Arbeidsgiver as ModelArbeidsgiver
import no.nav.syfo.model.Gradert as ModelGradert
import no.nav.syfo.model.HarArbeidsgiver as ModelHarArbeidsgiver
import no.nav.syfo.model.Periode as ModelPeriode
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent

fun DatabaseInterface.hentSykmeldinger(fnr: String): List<Sykmelding> =
        connection.use { connection ->
            connection.prepareStatement(
                    """
                SELECT OPPLYSNINGER.id,
                   mottatt_tidspunkt,
                   bekreftet_dato,
                   behandlingsutfall,
                   legekontor_org_nr,
                   STATUS.event,
                   STATUS.event_timestamp,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'skjermesForPasient')::jsonb         as skjermes_for_pasient,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'behandler')::jsonb ->> 'fornavn'    as lege_fornavn,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'behandler')::jsonb ->> 'mellomnavn' as lege_mellomnavn,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'behandler')::jsonb ->> 'etternavn'  as lege_etternavn,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'arbeidsgiver')::jsonb               as arbeidsgiver,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'perioder')::jsonb                   as perioder,
                   jsonb_extract_path(DOKUMENT.sykmelding, 'medisinskVurdering')::jsonb         as medisinsk_vurdering
            FROM SYKMELDINGSOPPLYSNINGER as OPPLYSNINGER
                     INNER JOIN SYKMELDINGSDOKUMENT as DOKUMENT on OPPLYSNINGER.id = DOKUMENT.id
                     LEFT OUTER JOIN SYKMELDINGSMETADATA as METADATA on OPPLYSNINGER.id = METADATA.id
                     INNER JOIN BEHANDLINGSUTFALL as UTFALL on OPPLYSNINGER.id = UTFALL.id
                     LEFT OUTER JOIN sykmeldingstatus as STATUS on OPPLYSNINGER.id = STATUS.sykmelding_id and STATUS.event_timestamp = (select STATUS.event_timestamp from sykmeldingstatus where STATUS.sykmelding_id = OPPLYSNINGER.id and STATUS.event = 'CONFIRMED' ORDER BY STATUS.event_timestamp desc limit 1)
            where pasient_fnr = ?;
            """
            ).use {
                it.setString(1, fnr)
                it.executeQuery().toList { toSykmelding() }
            }
        }

fun DatabaseInterface.registrerLestAvBruker(sykmeldingsid: String): Int =
        connection.use { connection ->
            val status = connection.prepareStatement(
                    """
            UPDATE SYKMELDINGSMETADATA
            SET bekreftet_dato = current_timestamp
            WHERE id = ? AND bekreftet_dato IS NULL;
            """
            ).use {
                it.setString(1, sykmeldingsid)
                it.executeUpdate()
            }
            connection.commit()
            return status
        }

fun DatabaseInterface.registerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
    connection.use { connection ->
        val status = connection.prepareStatement(
                """
                    INSERT INTO sykmeldingstatus(sykmelding_id, event_timestamp, event) VALUES (?, ?, ?)
                    """
        ).use {
            it.setString(1, sykmeldingStatusEvent.id)
            it.setTimestamp(2, Timestamp.valueOf(sykmeldingStatusEvent.timestamp))
            it.setString(3, sykmeldingStatusEvent.event.name)
            it.execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.erEier(sykmeldingsid: String, fnr: String): Boolean =
        connection.use { connection ->
            connection.prepareStatement(
                    """
            SELECT exists(SELECT 1 FROM SYKMELDINGSOPPLYSNINGER WHERE id=? AND pasient_fnr=?)
            """
            ).use {
                it.setString(1, sykmeldingsid)
                it.setString(2, fnr)
                it.executeQuery().next()
            }
        }

fun ResultSet.toSykmelding(): Sykmelding =
        Sykmelding(
                id = getString("id").trim(),
                skjermesForPasient = getBoolean("skjermes_for_pasient"),
                mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
                bekreftetDato = getBekreftedDato(),
                behandlingsutfall = objectMapper.readValue(getString("behandlingsutfall")),
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
                medisinskVurdering = objectMapper.readValue(getString("medisinsk_vurdering"))
        )

private fun ResultSet.getBekreftedDato(): LocalDateTime? {
    if (StatusEvent.CONFIRMED.name == getString("event")) {
        return getTimestamp("event_timestamp").toLocalDateTime()
    } else {
        return getTimestamp("bekreftet_dato")?.toLocalDateTime()
    }
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
