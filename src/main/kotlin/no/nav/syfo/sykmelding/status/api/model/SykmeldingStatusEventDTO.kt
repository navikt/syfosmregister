package no.nav.syfo.sykmelding.status.api.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.status.StatusEventDTO

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: OffsetDateTime
)
