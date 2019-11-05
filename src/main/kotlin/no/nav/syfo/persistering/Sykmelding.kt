package no.nav.syfo.persistering

import java.time.LocalDateTime
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

data class Sykmeldingsopplysninger(
    val id: String,
    val pasientFnr: String,
    val pasientAktoerId: String,
    val legeFnr: String,
    val legeAktoerId: String,
    val mottakId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val epjSystemNavn: String,
    val epjSystemVersjon: String,
    val mottattTidspunkt: LocalDateTime,
    val tssid: String?
)

data class Sykmeldingsdokument(
    val id: String,
    val sykmelding: Sykmelding
)

fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}

data class SykmeldingStatusEvent(
    val id: String,
    val timestamp: LocalDateTime,
    val event: StatusEvent
)

enum class StatusEvent {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET
}
