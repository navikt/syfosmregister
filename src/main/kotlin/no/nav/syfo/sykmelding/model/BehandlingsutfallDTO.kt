package no.nav.syfo.sykmelding.model

data class BehandlingsutfallDTO(
    val status: RegelStatusDTO,
    val ruleHits: List<RegelinfoDTO>
)
