package no.nav.syfo.persistering

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import java.sql.Connection
import java.sql.Timestamp

suspend fun DatabaseInterface.lagreMottattSykmelding(sykmeldingsopplysninger: Sykmeldingsopplysninger, sykmeldingsdokument: Sykmeldingsdokument) {
    connection.use { connection ->
        connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger)
        connection.opprettSykmeldingsdokument(sykmeldingsdokument)
        connection.commit()
    }
}

suspend fun DatabaseInterface.updateMottattSykmelding(sykmeldingsopplysninger: Sykmeldingsopplysninger, sykmeldingsdokument: Sykmeldingsdokument) = withContext(Dispatchers.IO) {
    connection.use { connection ->
        connection.updateSykmeldingsopplysninger(sykmeldingsopplysninger)
        connection.updateSykmeldingsdokument(sykmeldingsdokument)
        connection.commit()
    }
}

private suspend fun Connection.updateSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) = withContext(Dispatchers.IO) {
    prepareStatement(
        """
            update SYKMELDINGSDOKUMENT set id = ?, sykmelding = ? where id = ?;
            """,
    ).use {
        it.setString(1, sykmeldingsdokument.id)
        it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
        it.setString(3, sykmeldingsdokument.id)
        it.executeUpdate()
    }
}

private suspend fun Connection.updateSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) = withContext(Dispatchers.IO) {
    prepareStatement(
        """
        update sykmeldingsopplysninger set 
            pasient_fnr = ?,
            pasient_aktoer_id = ?,
            lege_fnr = ?,
            lege_hpr = ?,
            lege_helsepersonellkategori = ?,
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
            partnerreferanse = ?,
            utenlandsk_sykmelding = ?
        where id = ?;
    """,
    ).use {
        var i = 1
        it.setString(i++, sykmeldingsopplysninger.pasientFnr)
        it.setString(i++, sykmeldingsopplysninger.pasientAktoerId)
        it.setString(i++, sykmeldingsopplysninger.legeFnr)
        it.setString(i++, sykmeldingsopplysninger.legeHpr)
        it.setString(i++, sykmeldingsopplysninger.legeHelsepersonellkategori)
        it.setString(i++, sykmeldingsopplysninger.legeAktoerId)
        it.setString(i++, sykmeldingsopplysninger.mottakId)
        it.setString(i++, sykmeldingsopplysninger.legekontorOrgNr)
        it.setString(i++, sykmeldingsopplysninger.legekontorHerId)
        it.setString(i++, sykmeldingsopplysninger.legekontorReshId)
        it.setString(i++, sykmeldingsopplysninger.epjSystemNavn)
        it.setString(i++, sykmeldingsopplysninger.epjSystemVersjon)
        it.setTimestamp(i++, Timestamp.valueOf(sykmeldingsopplysninger.mottattTidspunkt))
        it.setString(i++, sykmeldingsopplysninger.tssid)
        it.setObject(i++, sykmeldingsopplysninger.merknader?.toPGObject())
        it.setString(i++, sykmeldingsopplysninger.partnerreferanse)
        it.setObject(i++, sykmeldingsopplysninger.utenlandskSykmelding?.toPGObject())
        it.setString(i, sykmeldingsopplysninger.id)
        it.executeUpdate()
    }
}

private suspend fun Connection.opprettSykmeldingsopplysninger(sykmeldingsopplysninger: Sykmeldingsopplysninger) = withContext(Dispatchers.IO) {
    prepareStatement(
        """
            INSERT INTO SYKMELDINGSOPPLYSNINGER(
                id,
                pasient_fnr,
                pasient_aktoer_id,
                lege_fnr,
                lege_hpr,
                lege_helsepersonellkategori,
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
                partnerreferanse,
                utenlandsk_sykmelding)
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
    ).use {
        var i = 1
        it.setString(i++, sykmeldingsopplysninger.id)
        it.setString(i++, sykmeldingsopplysninger.pasientFnr)
        it.setString(i++, sykmeldingsopplysninger.pasientAktoerId)
        it.setString(i++, sykmeldingsopplysninger.legeFnr)
        it.setString(i++, sykmeldingsopplysninger.legeHpr)
        it.setString(i++, sykmeldingsopplysninger.legeHelsepersonellkategori)
        it.setString(i++, sykmeldingsopplysninger.legeAktoerId)
        it.setString(i++, sykmeldingsopplysninger.mottakId)
        it.setString(i++, sykmeldingsopplysninger.legekontorOrgNr)
        it.setString(i++, sykmeldingsopplysninger.legekontorHerId)
        it.setString(i++, sykmeldingsopplysninger.legekontorReshId)
        it.setString(i++, sykmeldingsopplysninger.epjSystemNavn)
        it.setString(i++, sykmeldingsopplysninger.epjSystemVersjon)
        it.setTimestamp(i++, Timestamp.valueOf(sykmeldingsopplysninger.mottattTidspunkt))
        it.setString(i++, sykmeldingsopplysninger.tssid)
        it.setObject(i++, sykmeldingsopplysninger.merknader?.toPGObject())
        it.setString(i++, sykmeldingsopplysninger.partnerreferanse)
        it.setObject(i, sykmeldingsopplysninger.utenlandskSykmelding?.toPGObject())
        it.executeUpdate()
    }
}

private suspend fun Connection.opprettSykmeldingsdokument(sykmeldingsdokument: Sykmeldingsdokument) = withContext(Dispatchers.IO) {
    prepareStatement(
        """
            INSERT INTO SYKMELDINGSDOKUMENT(id, sykmelding) VALUES  (?, ?)
            """,
    ).use {
        it.setString(1, sykmeldingsdokument.id)
        it.setObject(2, sykmeldingsdokument.sykmelding.toPGObject())
        it.executeUpdate()
    }
}

suspend fun Connection.updateBehandlingsutfall(behandlingsutfall: Behandlingsutfall) = withContext(Dispatchers.IO) {
    use { connection ->
        connection.prepareStatement(
            """
           update behandlingsutfall set behandlingsutfall = ? where id = ?;
        """,
        ).use {
            it.setObject(1, behandlingsutfall.behandlingsutfall.toPGObject())
            it.setString(2, behandlingsutfall.id)
            it.executeUpdate()
        }
        connection.commit()
    }
}

suspend fun Connection.opprettBehandlingsutfall(behandlingsutfall: Behandlingsutfall) = withContext(Dispatchers.IO) {
    use { connection ->
        connection.prepareStatement(
            """
                    INSERT INTO BEHANDLINGSUTFALL(id, behandlingsutfall) VALUES (?, ?)
                """,
        ).use {
            it.setString(1, behandlingsutfall.id)
            it.setObject(2, behandlingsutfall.behandlingsutfall.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}

suspend fun Connection.erSykmeldingsopplysningerLagret(sykmeldingsid: String) = withContext(Dispatchers.IO) {
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM SYKMELDINGSOPPLYSNINGER
                WHERE id=?;
                """,
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeQuery().next()
        }
    }
}

suspend fun Connection.erBehandlingsutfallLagret(sykmeldingsid: String) = withContext(Dispatchers.IO) {
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM BEHANDLINGSUTFALL
                WHERE id=?;
                """,
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeQuery().next()
        }
    }
}
