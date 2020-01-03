package no.nav.syfo.persistering

import java.sql.Connection
import java.sql.Timestamp

fun Connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) {
    use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO SYKMELDINGSOPPLYSNINGER(
                id,
                pasient_fnr,
                pasient_aktoer_id,
                lege_fnr,
                lege_aktoer_id,
                mottak_id,
                legekontor_org_nr,
                legekontor_her_id,
                legekontor_resh_id,
                epj_system_navn,
                epj_system_versjon,
                mottatt_tidspunkt,
                tss_id)
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, sykmeldingsopplysninger.id)
            it.setString(2, sykmeldingsopplysninger.pasientFnr)
            it.setString(3, sykmeldingsopplysninger.pasientAktoerId)
            it.setString(4, sykmeldingsopplysninger.legeFnr)
            it.setString(5, sykmeldingsopplysninger.legeAktoerId)
            it.setString(6, sykmeldingsopplysninger.mottakId)
            it.setString(7, sykmeldingsopplysninger.legekontorOrgNr)
            it.setString(8, sykmeldingsopplysninger.legekontorHerId)
            it.setString(9, sykmeldingsopplysninger.legekontorReshId)
            it.setString(10, sykmeldingsopplysninger.epjSystemNavn)
            it.setString(11, sykmeldingsopplysninger.epjSystemVersjon)
            it.setTimestamp(12, Timestamp.valueOf(sykmeldingsopplysninger.mottattTidspunkt))
            it.setString(13, sykmeldingsopplysninger.tssid)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun Connection.opprettSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) {
    use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO SYKMELDINGSDOKUMENT(id, sykmelding) VALUES  (?, ?)
            """
        ).use {
            it.setString(1, sykmeldingsdokument.id)
            it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun Connection.opprettBehandlingsutfall(behandlingsutfall: Behandlingsutfall) =
    use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?)
                """
        ).use {
            it.setString(1, behandlingsutfall.id)
            it.setObject(2, behandlingsutfall.behandlingsutfall.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }

fun Connection.erSykmeldingsopplysningerLagret(sykmeldingsid: String) =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM SYKMELDINGSOPPLYSNINGER
                WHERE id=?;
                """
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeQuery().next()
        }
    }

fun Connection.erBehandlingsutfallLagret(sykmeldingsid: String) =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM BEHANDLINGSUTFALL
                WHERE id=?;
                """
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeQuery().next()
        }
    }
