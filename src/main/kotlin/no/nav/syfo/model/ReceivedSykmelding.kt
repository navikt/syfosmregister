package no.nav.syfo.model

import java.time.LocalDateTime

data class ReceivedSykmelding(
    val sykmelding: Sykmelding,
    val aktoerIdPasient: String,
    val aktoerIdLege: String,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime
)
