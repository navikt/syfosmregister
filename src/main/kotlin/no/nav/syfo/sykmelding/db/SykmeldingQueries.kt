package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype

fun DatabaseInterface.getArbeidsgiverStatus(sykmeldingId: String): ArbeidsgiverDbModel? =
    connection.use { connection ->
        connection
            .prepareStatement(
                """
           Select * from arbeidsgiver where sykmelding_id = ? 
        """,
            )
            .use { ps ->
                ps.setString(1, sykmeldingId)
                ps.executeQuery().use {
                    when (it.next()) {
                        true ->
                            ArbeidsgiverDbModel(
                                orgnummer = it.getString("orgnummer"),
                                juridiskOrgnummer = it.getString("juridisk_orgnummer"),
                                orgNavn = it.getString("navn"),
                            )
                        else -> null
                    }
                }
            }
    }

suspend fun DatabaseInterface.getSykmeldinger(fnr: String): List<SykmeldingDbModel> =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getSykmeldingMedSisteStatus(fnr) }
    }

suspend fun DatabaseInterface.getSykmeldingerMedId(id: String): SykmeldingDbModel? =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getSykmeldingMedSisteStatusForId(id) }
    }

suspend fun DatabaseInterface.getSykmelding(sykmeldingId: String, fnr: String): SykmeldingDbModel? =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.getSykmelding(sykmeldingId, fnr) }
    }

suspend fun DatabaseInterface.getSykmeldingerMedIdUtenBehandlingsutfall(id: String) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfall(id)
        }
    }

suspend fun DatabaseInterface.getSykmeldingerMedIdUtenBehandlingsutfallForFnr(
    fnr: String
): List<SykmeldingDbModelUtenBehandlingsutfall> =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfallForFnr(fnr)
        }
    }

suspend fun DatabaseInterface.hentSporsmalOgSvar(id: String): List<Sporsmal> =
    withContext(Dispatchers.IO) {
        connection.use { connection -> connection.hentSporsmalOgSvar(id) }
    }

suspend fun DatabaseInterface.updateFnr(fnr: String, nyttFnr: String): Int =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            var updated: Int
            connection
                .prepareStatement(
                    """
            UPDATE sykmeldingsopplysninger set pasient_fnr = ? where pasient_fnr = ?;
        """,
                )
                .use {
                    it.setString(1, nyttFnr)
                    it.setString(2, fnr)
                    updated = it.executeUpdate()
                }
            connection.commit()
            updated
        }
    }

private fun Connection.getSykmeldingMedSisteStatus(fnr: String): List<SykmeldingDbModel> =
    prepareStatement(
            """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader,
                    utenlandsk_sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where pasient_fnr = ?;
                    """,
        )
        .use {
            it.setString(1, fnr)
            it.executeQuery().toList { toSykmeldingDbModel() }
        }

private fun Connection.getSykmeldingMedSisteStatusForId(id: String): SykmeldingDbModel? =
    prepareStatement(
            """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader,
                    utenlandsk_sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where opplysninger.id = ?;
                    """,
        )
        .use {
            it.setString(1, id)
            it.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
        }

private fun Connection.getSykmelding(id: String, fnr: String): SykmeldingDbModel? =
    prepareStatement(
            """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader,
                    utenlandsk_sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where opplysninger.id = ?
                    and pasient_fnr = ?;
                    """,
        )
        .use {
            it.setString(1, id)
            it.setString(2, fnr)
            it.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
        }

private fun Connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfall(
    id: String
): SykmeldingDbModelUtenBehandlingsutfall? =
    prepareStatement(
            """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader,
                    utenlandsk_sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where opplysninger.id = ?;
                    """,
        )
        .use {
            it.setString(1, id)
            it.executeQuery().toList { toSykmeldingDbModelUtenBehandlingsutfall() }.firstOrNull()
        }

private fun Connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfallForFnr(
    fnr: String
): List<SykmeldingDbModelUtenBehandlingsutfall> =
    prepareStatement(
            """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    merknader,
                    utenlandsk_sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where pasient_fnr = ?;
                    """,
        )
        .use {
            it.setString(1, fnr)
            it.executeQuery().toList { toSykmeldingDbModelUtenBehandlingsutfall() }
        }

fun Connection.hentSporsmalOgSvar(sykmeldingId: String): List<Sporsmal> =
    prepareStatement(
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
                WHERE svar.sykmelding_id = ? order by sporsmal.id;
            """,
        )
        .use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { tilSporsmal() }
        }

fun finnPeriodetype(sykmeldingsDokument: Sykmelding): Periodetype =
    when {
        sykmeldingsDokument.perioder.first().aktivitetIkkeMulig != null ->
            Periodetype.AKTIVITET_IKKE_MULIG
        sykmeldingsDokument.perioder.first().gradert != null -> Periodetype.GRADERT
        else -> throw RuntimeException("Kunne ikke bestemme typen til periode")
    }

fun ResultSet.toSykmeldingDbModel(): SykmeldingDbModel {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return SykmeldingDbModel(
        sykmeldingsDokument =
            objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        id = getString("id"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        legekontorOrgNr = getString("legekontor_org_nr"),
        behandlingsutfall =
            objectMapper.readValue(getString("behandlingsutfall"), ValidationResult::class.java),
        status = getStatus(mottattTidspunkt),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) },
        utenlandskSykmelding =
            getString("utenlandsk_sykmelding")?.let {
                objectMapper.readValue<UtenlandskSykmelding>(it)
            },
    )
}

fun ResultSet.toSykmeldingDbModelUtenBehandlingsutfall(): SykmeldingDbModelUtenBehandlingsutfall {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return SykmeldingDbModelUtenBehandlingsutfall(
        sykmeldingsDokument =
            objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        id = getString("id"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        legekontorOrgNr = getString("legekontor_org_nr"),
        status = getStatus(mottattTidspunkt),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) },
        utenlandskSykmelding =
            getString("utenlandsk_sykmelding")?.let {
                objectMapper.readValue<UtenlandskSykmelding>(it)
            },
    )
}

private fun ResultSet.getStatus(mottattTidspunkt: OffsetDateTime): StatusDbModel {
    return when (val status = getString("event")) {
        null -> StatusDbModel(StatusEvent.APEN.name, mottattTidspunkt, null)
        else -> {
            val statusTimestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
            val arbeidsgiverDbModel =
                when (status) {
                    StatusEvent.SENDT.name ->
                        ArbeidsgiverDbModel(
                            orgnummer = getString("orgnummer"),
                            juridiskOrgnummer = getString("juridisk_orgnummer"),
                            orgNavn = getString("navn"),
                        )
                    else -> null
                }
            return StatusDbModel(status, statusTimestamp, arbeidsgiverDbModel)
        }
    }
}

fun ResultSet.tilSporsmal(): Sporsmal =
    Sporsmal(
        tekst = getString("tekst"),
        shortName = tilShortName(getString("shortname")),
        svar = tilSvar(),
    )

fun ResultSet.tilSvar(): Svar =
    Svar(
        sykmeldingId = getString("sykmelding_id"),
        sporsmalId = getInt("sporsmal_id"),
        svartype = tilSvartype(getString("svartype")),
        svar = getString("svar"),
    )

private fun tilShortName(shortname: String): ShortName {
    return when (shortname) {
        "ARBEIDSSITUASJON" -> ShortName.ARBEIDSSITUASJON
        "FORSIKRING" -> ShortName.FORSIKRING
        "FRAVAER" -> ShortName.FRAVAER
        "PERIODE" -> ShortName.PERIODE
        "NY_NARMESTE_LEDER" -> ShortName.NY_NARMESTE_LEDER
        "EGENMELDINGSDAGER" -> ShortName.EGENMELDINGSDAGER
        else ->
            throw IllegalStateException(
                "Sykmeldingen har en ukjent spørsmålskode, skal ikke kunne skje"
            )
    }
}

private fun tilSvartype(svartype: String): Svartype {
    return when (svartype) {
        "ARBEIDSSITUASJON" -> Svartype.ARBEIDSSITUASJON
        "PERIODER" -> Svartype.PERIODER
        "JA_NEI" -> Svartype.JA_NEI
        "DAGER" -> Svartype.DAGER
        else ->
            throw IllegalStateException("Sykmeldingen har en ukjent svartype, skal ikke kunne skje")
    }
}
