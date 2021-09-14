package no.nav.syfo.sykmelding.status

import java.sql.ResultSet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList

fun DatabaseInterface.finnSvarForSykmelding(sykmeldingId: String): List<Sporsmal> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM svar as SVAR
                     INNER JOIN sporsmal as SPM on SVAR.sporsmal_id = SPM.id
                WHERE sykmelding_id=?;
                """
        ).use {
            it.setString(1, sykmeldingId)
            return it.executeQuery().toList { tilSporsmal() }
        }
    }
}

fun ResultSet.tilSporsmal(): Sporsmal =
    Sporsmal(
        tekst = getString("tekst"),
        shortName = ShortName.valueOf(getString("shortname")),
        svar = Svar(
            sykmeldingId = getString("sykmelding_id"),
            sporsmalId = getInt("sporsmal_id"),
            svartype = Svartype.valueOf(getString("svartype")),
            svar = getString("svar")
        )
    )
