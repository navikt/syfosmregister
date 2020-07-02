package no.nav.syfo.sykmelding.serviceuser.api.model

import java.time.LocalDate

data class StatusRequest(
    val fnr: String,
    val dato: LocalDate
)

data class SykmeldtStatus(
    val erSykmeldt: Boolean,
    val gradert: Boolean?
)
