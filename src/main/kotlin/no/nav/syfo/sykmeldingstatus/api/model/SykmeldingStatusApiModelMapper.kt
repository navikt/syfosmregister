package no.nav.syfo.sykmeldingstatus.api.model

import java.time.ZonedDateTime
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.api.toStatusEventDTO
import no.nav.syfo.util.TimestampUtil

class SykmeldingStatusApiModelMapper private constructor() {
    companion object {
        fun toSykmeldingStatusList(sykmeldingstatusList: List<SykmeldingStatusEvent>): List<SykmeldingStatusEventDTO> {
            return sykmeldingstatusList.map { toSykmeldingStatusEventDTO(it) }
        }

        fun toSykmeldingStatusEventDTO(sykmeldingStatusEvent: SykmeldingStatusEvent): SykmeldingStatusEventDTO {
            return SykmeldingStatusEventDTO(
                    statusEvent = sykmeldingStatusEvent.event.toStatusEventDTO(),
                    timestamp = getStatusUTCTime(sykmeldingStatusEvent)
            )
        }

        private fun getStatusUTCTime(sykmeldingStatusEvent: SykmeldingStatusEvent): ZonedDateTime {
            return when (sykmeldingStatusEvent.timestampz) {
                null -> TimestampUtil.getAdjustedZonedDateTime(sykmeldingStatusEvent.timestamp)
                else -> sykmeldingStatusEvent.timestampz
            }
        }
    }
}
