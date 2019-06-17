package no.nav.syfo.nullstilling

import no.nav.syfo.db.DatabaseInterface
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun DatabaseInterface.nullstillSykmeldinger(aktorId: String) {
    val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")
    connection.use { connection ->
        log.info("Nullstiller sykmeldinger på aktor: {}", aktorId)
        connection.prepareStatement(
            """
                DELETE FROM behandlingsutfall
                WHERE id IN (
                    SELECT id
                    FROM sykmeldingsopplysninger
                    WHERE pasient_aktoer_id=?
                );

                DELETE FROM SYKMELDINGSOPPLYSNINGER
                WHERE pasient_aktoer_id=?;
            """
        ).use {
            it.setString(1, aktorId)
            it.setString(2, aktorId)
            it.execute()
        }
        connection.commit()

        log.info("Utført: slettet sykmeldinger på aktør: {}", aktorId)
    }
}
