package no.nav.syfo.sykmeldingstatus.api.model

import java.time.ZonedDateTime
import no.nav.syfo.sykmeldingstatus.StatusEventDTO

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: ZonedDateTime
)
