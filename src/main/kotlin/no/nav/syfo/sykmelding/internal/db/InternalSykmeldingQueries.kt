package no.nav.syfo.sykmelding.internal.db

import java.sql.Connection
import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult

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
                    sykmelding
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        INNER JOIN behandlingsutfall AS utfall ON opplysninger.id = utfall.id
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
    return SykmeldingDbModel(sykmeldingsDokument = getObject("sykmelding", Sykmelding::class.java),
            id = getString("id"),
            mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
            legekontorOrgNr = getString("legekontor_org_nr"),
            behandlingsutfall = getObject("behandlingsutfall", ValidationResult::class.java),
            status = getString("event"),
            status_timestamp = getTimestamp("event_timestamp").toLocalDateTime()
    )
}
