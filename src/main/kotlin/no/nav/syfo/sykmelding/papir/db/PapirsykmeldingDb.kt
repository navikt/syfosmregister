package no.nav.syfo.sykmelding.papir.db

import java.sql.ResultSet
import java.time.ZoneOffset
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.Sykmelding

fun DatabaseInterface.getPapirsykmelding(sykmeldingId: String): PapirsykmeldingDbModel? =
    connection.use { connection ->
        return connection
            .prepareStatement(
                """
                    SELECT mottatt_tidspunkt,
                    sykmelding,
                    opplysninger.pasient_fnr
                    FROM sykmeldingsopplysninger AS opplysninger
                        INNER JOIN sykmeldingsdokument AS dokument ON opplysninger.id = dokument.id
                    where opplysninger.id = ?;
                    """,
            )
            .use {
                it.setString(1, sykmeldingId)
                it.executeQuery()
                    .toList { toPapirsykmeldingDbModel() }
                    .firstOrNull { result ->
                        result.sykmelding.avsenderSystem.navn == "Papirsykmelding"
                    }
            }
    }

fun ResultSet.toPapirsykmeldingDbModel(): PapirsykmeldingDbModel {
    return PapirsykmeldingDbModel(
        sykmelding = objectMapper.readValue(getString("sykmelding"), Sykmelding::class.java),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toInstant().atOffset(ZoneOffset.UTC),
        pasientFnr = getString("pasient_fnr"),
    )
}
