package no.nav.syfo.sykmelding.papir.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.Sykmelding
import java.sql.ResultSet
import java.time.ZoneOffset

fun DatabaseInterface.getPapirsykmelding(sykmeldingId: String): PapirsykmeldingDbModel? =
    connection.use { connection ->
        return connection.prepareStatement(
            """
                    SELECT mottatt_tidspunkt,
                    sykmelding,
                    arbeidsgiver.orgnummer,
                    arbeidsgiver.juridisk_orgnummer,
                    arbeidsgiver.navn,
                    opplysninger.pasient_aktoer_id,
                    opplysninger.pasient_fnr
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                        LEFT OUTER JOIN arbeidsgiver as arbeidsgiver on arbeidsgiver.sykmelding_id = opplysninger.id
                    where opplysninger.id = ?
                    and not exists(select 1 from sykmeldingstatus where sykmelding_id = opplysninger.id and event in ('SLETTET'));
                    """
        ).use {
            it.setString(1, sykmeldingId)
            it.executeQuery().toList { toPapirsykmeldingDbModel() }.firstOrNull { result ->
                result.sykmelding.avsenderSystem.navn == "Papirsykmelding"
            }
        }
    }

fun ResultSet.toPapirsykmeldingDbModel(): PapirsykmeldingDbModel {
    return PapirsykmeldingDbModel(
        sykmelding = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        pasientAktoerId = getString("pasient_aktoer_id"),
        pasientFnr = getString("pasient_fnr")
    )
}
