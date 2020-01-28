package no.nav.syfo.sykmelding.internal.model

import java.time.LocalDateTime

data class SykmeldingStatusDTO(
    val status: String,
    val timestamp: LocalDateTime
)
