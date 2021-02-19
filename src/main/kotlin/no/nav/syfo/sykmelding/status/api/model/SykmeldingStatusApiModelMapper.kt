package no.nav.syfo.sykmelding.status.api.model

import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.api.toStatusEventDTO

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
