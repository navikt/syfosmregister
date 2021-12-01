package no.nav.syfo.persistering

import no.nav.syfo.model.Merknad
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDateTime

data class Sykmeldingsopplysninger(
    val id: String,
    val pasientFnr: String,
    val pasientAktoerId: String,
    val legeFnr: String,
    val legeHpr: String?,
    val legeHelsepersonellkategori: String?,
    val legeAktoerId: String,
    val mottakId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val epjSystemNavn: String,
    val epjSystemVersjon: String,
    val mottattTidspunkt: LocalDateTime,
    val tssid: String?,
    val merknader: List<Merknad>?,
    val partnerreferanse: String?
)

data class Sykmeldingsdokument(
    val id: String,
    val sykmelding: Sykmelding
)

fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

fun List<Merknad>.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
