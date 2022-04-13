package no.nav.syfo.sykmelding.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun DatabaseInterface.getArbeidsgiverStatus(sykmeldingId: String): ArbeidsgiverDbModel? {
    return connection.use { connection ->
        connection.prepareStatement(
            """
           Select * from arbeidsgiver where sykmelding_id = ? 
        """
        ).use { ps ->
            ps.setString(1, sykmeldingId)
            ps.executeQuery().use {
                when (it.next()) {
                    true -> ArbeidsgiverDbModel(
                        orgnummer = it.getString("orgnummer"),
                        juridiskOrgnummer = it.getString("juridisk_orgnummer"),
                        orgNavn = it.getString("navn")
                    )
                    else -> null
                }
            }
        }
    }
}

fun DatabaseInterface.getSykmeldinger(fnr: String): List<SykmeldingDbModel> =
    connection.use { connection ->
        return connection.getSykmeldingMedSisteStatus(fnr)
    }

fun DatabaseInterface.getSykmeldingerMedId(id: String): SykmeldingDbModel? =
    connection.use { connection ->
        return connection.getSykmeldingMedSisteStatusForId(id)
    }

fun DatabaseInterface.getSykmelding(sykmeldingId: String, fnr: String): SykmeldingDbModel? =
    connection.use { connection ->
        return connection.getSykmelding(sykmeldingId, fnr)
    }

fun DatabaseInterface.getSykmeldingerMedIdUtenBehandlingsutfall(id: String): SykmeldingDbModelUtenBehandlingsutfall? =
    connection.use { connection ->
        return connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfall(id)
    }

fun DatabaseInterface.getSykmeldingerMedIdUtenBehandlingsutfallForFnr(fnr: String): List<SykmeldingDbModelUtenBehandlingsutfall> =
    connection.use { connection ->
        return connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfallForFnr(fnr)
    }

fun DatabaseInterface.hentSporsmalOgSvar(id: String): List<Sporsmal> {
    connection.use { connection ->
        return connection.hentSporsmalOgSvar(id)
    }
}

fun DatabaseInterface.updateFnr(fnr: String, nyttFnr: String): Int {
    connection.use { connection ->
        var updated = 0
        connection.prepareStatement(
            """
            UPDATE sykmeldingsopplysninger set pasient_fnr = ? where pasient_fnr = ?;
        """
        ).use {
            it.setString(1, nyttFnr)
            it.setString(2, fnr)
            updated = it.executeUpdate()
        }
        connection.commit()
        return updated
    }
}

private fun Connection.getSykmeldingMedSisteStatus(fnr: String): List<SykmeldingDbModel> =
    this.prepareStatement(
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
                    merknader
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
                    where pasient_fnr = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toSykmeldingDbModel() }
    }

private fun Connection.getSykmeldingMedSisteStatusForId(id: String): SykmeldingDbModel? =
    this.prepareStatement(
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
                    merknader
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
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, id)
        it.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
    }

private fun Connection.getSykmelding(id: String, fnr: String): SykmeldingDbModel? =
    this.prepareStatement(
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
                    merknader
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
                    and pasient_fnr = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, id)
        it.setString(2, fnr)
        it.executeQuery().toList { toSykmeldingDbModel() }.firstOrNull()
    }

private fun Connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfall(id: String): SykmeldingDbModelUtenBehandlingsutfall? =
    this.prepareStatement(
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
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where opplysninger.id = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, id)
        it.executeQuery().toList { toSykmeldingDbModelUtenBehandlingsutfall() }.firstOrNull()
    }

private fun Connection.getSykmeldingMedSisteStatusForIdUtenBehandlingsutfallForFnr(fnr: String): List<SykmeldingDbModelUtenBehandlingsutfall> =
    this.prepareStatement(
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
                    merknader
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.timestamp = (SELECT timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY timestamp DESC
                                                                                             LIMIT 1)
                    where pasient_fnr = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toSykmeldingDbModelUtenBehandlingsutfall() }
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

fun ResultSet.toSykmeldingDbModel(): SykmeldingDbModel {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return SykmeldingDbModel(
        sykmeldingsDokument = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        id = getString("id"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        legekontorOrgNr = getString("legekontor_org_nr"),
        behandlingsutfall = objectMapper.readValue(getString("behandlingsutfall"), ValidationResult::class.java),
        status = getStatus(mottattTidspunkt),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) }
    )
}

fun ResultSet.toSykmeldingDbModelUtenBehandlingsutfall(): SykmeldingDbModelUtenBehandlingsutfall {
    val mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC)
    return SykmeldingDbModelUtenBehandlingsutfall(
        sykmeldingsDokument = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        id = getString("id"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        legekontorOrgNr = getString("legekontor_org_nr"),
        status = getStatus(mottattTidspunkt),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) }
    )
}

private fun ResultSet.getStatus(mottattTidspunkt: OffsetDateTime): StatusDbModel {
    return when (val status = getString("event")) {
        null -> StatusDbModel(StatusEvent.APEN.name, mottattTidspunkt, null)
        else -> {
            val status_timestamp = getTimestamp("timestamp").toInstant().atOffset(ZoneOffset.UTC)
            val arbeidsgiverDbModel = when (status) {
                StatusEvent.SENDT.name -> ArbeidsgiverDbModel(
                    orgnummer = getString("orgnummer"),
                    juridiskOrgnummer = getString("juridisk_orgnummer"),
                    orgNavn = getString("navn")
                )
                else -> null
            }
            return StatusDbModel(status, status_timestamp, arbeidsgiverDbModel)
        }
    }
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
