package no.nav.syfo.persistering

import java.sql.Connection
import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface

fun DatabaseInterface.lagreMottattSykmelding(sykmeldingsopplysninger: Sykmeldingsopplysninger, sykmeldingsdokument: Sykmeldingsdokument) {
    connection.use { connection ->
        connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger)
        connection.opprettSykmeldingsdokument(sykmeldingsdokument)
        connection.commit()
    }
}

fun DatabaseInterface.updateMottattSykmelding(sykmeldingsopplysninger: Sykmeldingsopplysninger, sykmeldingsdokument: Sykmeldingsdokument) {
    connection.use { connection ->
        connection.updateSykmeldingsopplysninger(sykmeldingsopplysninger)
        connection.updateSykmeldingsdokument(sykmeldingsdokument)
        connection.commit()
    }
}

private fun Connection.updateSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) {
    this.prepareStatement(
        """
            update SYKMELDINGSDOKUMENT set id = ?, sykmelding = ? where id = ?;
            """
    ).use {
        it.setString(1, sykmeldingsdokument.id)
        it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
        it.setString(3, sykmeldingsdokument.id)
        it.executeUpdate()
    }
}

private fun Connection.updateSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) {
    this.prepareStatement("""
        update sykmeldingsopplysninger set 
            pasient_fnr = ?,
            pasient_aktoer_id = ?,
            lege_fnr = ?,
            lege_aktoer_id = ?,
            mottak_id = ?,
            legekontor_org_nr = ?,
            legekontor_her_id = ?,
            legekontor_resh_id = ?,
            epj_system_navn = ?,
            epj_system_versjon = ?,
            mottatt_tidspunkt = ?,
            tss_id = ?,
            merknader = ?,
            partnerreferanse = ?
        where id = ?;
    """).use {
        it.setString(1, sykmeldingsopplysninger.pasientFnr)
        it.setString(2, sykmeldingsopplysninger.pasientAktoerId)
        it.setString(3, sykmeldingsopplysninger.legeFnr)
        it.setString(4, sykmeldingsopplysninger.legeAktoerId)
        it.setString(5, sykmeldingsopplysninger.mottakId)
        it.setString(6, sykmeldingsopplysninger.legekontorOrgNr)
        it.setString(7, sykmeldingsopplysninger.legekontorHerId)
        it.setString(8, sykmeldingsopplysninger.legekontorReshId)
        it.setString(9, sykmeldingsopplysninger.epjSystemNavn)
        it.setString(10, sykmeldingsopplysninger.epjSystemVersjon)
        it.setTimestamp(11, Timestamp.valueOf(sykmeldingsopplysninger.mottattTidspunkt))
        it.setString(12, sykmeldingsopplysninger.tssid)
        it.setObject(13, sykmeldingsopplysninger.merknader?.toPGObject())
        it.setString(14, sykmeldingsopplysninger.partnerreferanse)
        it.setString(15, sykmeldingsopplysninger.id)
        it.executeUpdate()
    }
}

private fun Connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) {
    this.prepareStatement(
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
                tss_id,
                merknader,
                partnerreferanse)
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        it.setObject(14, sykmeldingsopplysninger.merknader?.toPGObject())
        it.setString(15, sykmeldingsopplysninger.partnerreferanse)
        it.executeUpdate()
    }
}

private fun Connection.opprettSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) {
    this.prepareStatement(
        """
            INSERT INTO SYKMELDINGSDOKUMENT(id, sykmelding) VALUES  (?, ?)
            """
    ).use {
        it.setString(1, sykmeldingsdokument.id)
        it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
        it.executeUpdate()
    }
}

fun Connection.updateBehandlingsutfall(behandlingsutfall: Behandlingsutfall) {
    use { connection ->
        connection.prepareStatement("""
           update behandlingsutfall set behandlingsutfall = ? where id = ?;
        """).use {
            it.setObject(1, behandlingsutfall.behandlingsutfall.toPGObject())
            it.setString(2, behandlingsutfall.id)
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
