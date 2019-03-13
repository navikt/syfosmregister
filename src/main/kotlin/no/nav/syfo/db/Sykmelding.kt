package no.nav.syfo.db

import java.time.LocalDateTime


data class SykemdlingDB(
        val pasientfnr: String,
        val pasientaktorid: String,
        val legefnr: String,
        val legeaktorid: String,
        val mottakid: String,
        val legekontororgnr: String?,
        val legekontorherid: String?,
        val legekontorreshid: String?,
        val legekontororgname: String?,
        val epjsystem: String,
        val epjversjon: String,
        val mottatttidspunkt: LocalDateTime,
        val sykemelding: String
)

fun insert(table: String, coloums: SykemdlingDB): String =
        "INSERT INTO $table (pasientfnr,pasientaktorid,legefnr,legeaktorid," +
                "mottakid,legekontororgnr,legekontorherid,legekontorreshid," +
                "legekontororgname,epjsystem,epjversjon,mottatttidspunkt,sykemelding) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?', ?, ?, ?')"
