package no.nav.syfo.sykmelding.status.api.model

import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.StatusEventDTO
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent

class SykmeldingStatusApiModelMapper private constructor() {
    companion object {
        fun toSykmeldingStatusList(sykmeldingstatusList: List<SykmeldingStatusEvent>): List<SykmeldingStatusEventDTO> {
            return sykmeldingstatusList.map { toSykmeldingStatusEventDTO(it) }
        }

        fun toSykmeldingStatusEventDTO(sykmeldingStatusEvent: SykmeldingStatusEvent): SykmeldingStatusEventDTO {
            return SykmeldingStatusEventDTO(
                statusEvent = sykmeldingStatusEvent.event.toStatusEventDTO(),
                timestamp = sykmeldingStatusEvent.timestamp,
                erAvvist = sykmeldingStatusEvent.erAvvist,
                erEgenmeldt = sykmeldingStatusEvent.erEgenmeldt
            )
        }
    }
}

fun StatusEvent.toStatusEventDTO(): StatusEventDTO {
    return when (this) {
        StatusEvent.BEKREFTET -> StatusEventDTO.BEKREFTET
        StatusEvent.APEN -> StatusEventDTO.APEN
        StatusEvent.SENDT -> StatusEventDTO.SENDT
        StatusEvent.AVBRUTT -> StatusEventDTO.AVBRUTT
        StatusEvent.UTGATT -> StatusEventDTO.UTGATT
        StatusEvent.SLETTET -> throw IllegalStateException("Sykmeldingen er slettet, skal ikke kunne skje")
    }
}
