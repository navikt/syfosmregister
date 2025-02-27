package no.nav.syfo.juridiskvurdering

import java.sql.Date
import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface

class JuridiskVurderingDB(val databaseInterface: DatabaseInterface) {

    fun insertOrUpdate(vurdering: JuridiskVurdering, tilbakedateringInputs: TilbakedateringInputs) {
        databaseInterface.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                INSERT INTO tilbakedatert_vurdering (
                    sykmelding_id,
                    fom,
                    tom,
                    genereringstidspunkt,
                    ettersending_av,
                    forlengelse_av,
                    syketilfellet_startdato,
                    arbeidsgiverperiode,
                    diagnose_system,
                    diagnose_kode,
                    opprettet,
                    vurdering
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (sykmelding_id) DO UPDATE SET
                    fom = EXCLUDED.fom,
                    tom = EXCLUDED.tom,
                    genereringstidspunkt = EXCLUDED.genereringstidspunkt,
                    ettersending_av = EXCLUDED.ettersending_av,
                    forlengelse_av = EXCLUDED.forlengelse_av,
                    syketilfellet_startdato = EXCLUDED.syketilfellet_startdato,
                    arbeidsgiverperiode = EXCLUDED.arbeidsgiverperiode,
                    diagnose_system = EXCLUDED.diagnose_system,
                    diagnose_kode = EXCLUDED.diagnose_kode,
                    opprettet = EXCLUDED.opprettet,
                    vurdering = EXCLUDED.vurdering
                """
                )
                .use { ps ->
                    val sykmeldingId =
                        vurdering.sporing["sykmelding"] ?: error("SykmeldingId mangler")

                    ps.setString(1, sykmeldingId)
                    ps.setDate(2, Date.valueOf(tilbakedateringInputs.fom))
                    ps.setDate(3, tilbakedateringInputs.tom?.let { Date.valueOf(it) })
                    ps.setDate(4, Date.valueOf(tilbakedateringInputs.genereringstidspunkt))
                    ps.setString(5, tilbakedateringInputs.ettersendingAv)
                    ps.setString(6, tilbakedateringInputs.forlengelseAv)
                    ps.setDate(
                        7,
                        tilbakedateringInputs.syketilfelletStartdato?.let { Date.valueOf(it) }
                    )
                    ps.setObject(8, tilbakedateringInputs.arbeidsgiverperiode)
                    ps.setString(9, tilbakedateringInputs.diagnoseSystem)
                    ps.setString(10, tilbakedateringInputs.diagnoseKode)
                    ps.setTimestamp(11, Timestamp.from(vurdering.tidsstempel.toInstant()))
                    ps.setString(12, vurdering.utfall.name)
                    ps.execute()
                }
            connection.commit()
        }
    }
}
