package no.nav.syfo.sykmelding.status.api.model

import no.nav.syfo.sykmelding.status.StatusEventDTO
import java.time.OffsetDateTime

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: OffsetDateTime,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)
