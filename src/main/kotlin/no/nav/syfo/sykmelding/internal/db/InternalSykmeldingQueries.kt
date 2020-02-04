package no.nav.syfo.sykmelding.internal.db

import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.StatusEvent

fun DatabaseInterface.getInternalSykmelding(fnr: String): List<SykmeldingDbModel> =
        connection.use { connection ->
            return connection.getInternalSykmeldingMedSisteStatus(fnr)
        }

private fun Connection.getInternalSykmeldingMedSisteStatus(fnr: String): List<SykmeldingDbModel> =
        this.prepareStatement(
                """
                    SELECT opplysninger.id,
                    mottatt_tidspunkt,
                    behandlingsutfall,
                    legekontor_org_nr,
                    sykmelding,
                    status.event,
                    status.event_timestamp,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                        LEFT OUTER JOIN sykmeldingstatus AS status ON opplysninger.id = status.sykmelding_id AND
                                                                   status.event_timestamp = (SELECT event_timestamp
                                                                                             FROM sykmeldingstatus
                                                                                             WHERE sykmelding_id = opplysninger.id
                                                                                             ORDER BY event_timestamp DESC
                                                                                             LIMIT 1)
                    where pasient_fnr = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
        ).use {
            it.setString(1, fnr)
            it.executeQuery().toList { toSykmeldingDbModel() }
        }

fun ResultSet.toSykmeldingDbModel(): SykmeldingDbModel {
    return SykmeldingDbModel(sykmeldingsDokument = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
            id = getString("id"),
            mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
            legekontorOrgNr = getString("legekontor_org_nr"),
            behandlingsutfall = objectMapper.readValue(getString("behandlingsutfall"), ValidationResult::class.java),
            status = getStatus()
    )
}

private fun ResultSet.getStatus(): StatusDbModel {
    val status = getString("event")
    val status_timestamp = getTimestamp("event_timestamp").toLocalDateTime()
    val arbeidsgiverDbModel = when (status) {
        StatusEvent.SENDT.name -> ArbeidsgiverDbModel(
                orgNr = getString("orgnummer"),
                juridiskOrgNr = getString("juridisk_orgnummer"),
                navn = getString("navn")
        )
        else -> null
    }
    return StatusDbModel(status, status_timestamp, arbeidsgiverDbModel)
}
