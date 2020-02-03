package no.nav.syfo.sykmelding.internal.model

data class RegelinfoDTO(
    val messageForSender: String,
    val messageForUser: String,
    val ruleName: String,
    val ruleStatus: RegelStatusDTO?
)
