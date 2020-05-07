package no.nav.syfo.sykmelding.status.api.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.api.toStatusEventDTO
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

        private fun getStatusUTCTime(sykmeldingStatusEvent: SykmeldingStatusEvent): OffsetDateTime {
            return when (sykmeldingStatusEvent.timestampz) {
                null -> TimestampUtil.getAdjustedOffsetDateTime(sykmeldingStatusEvent.timestamp)
                else -> sykmeldingStatusEvent.timestampz
            }
        }
    }
}
