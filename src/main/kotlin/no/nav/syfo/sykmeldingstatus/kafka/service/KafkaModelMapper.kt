package no.nav.syfo.sykmeldingstatus.kafka.service

import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.ArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.util.TimestampUtil.Companion.getAdjustedToLocalDateTime

class KafkaModelMapper private constructor() {
    companion object {
        fun toArbeidsgiverStatus(sykmeldingId: String, arbeidsgiver: ArbeidsgiverStatusDTO) =
                ArbeidsgiverStatus(sykmeldingId, arbeidsgiver.orgnummer, arbeidsgiver.juridiskOrgnummer, arbeidsgiver.orgNavn)

        fun toSporsmal(sporsmal: SporsmalOgSvarDTO, sykmeldingId: String): Sporsmal {
            return Sporsmal(sporsmal.tekst, toShortName(sporsmal.shortName), toSvar(sporsmal, sykmeldingId))
        }

        fun toSykmeldingStatusEvent(event: SykmeldingStatusKafkaEventDTO): SykmeldingStatusEvent {
            return SykmeldingStatusEvent(event.sykmeldingId, getAdjustedToLocalDateTime(event.timestamp), toStatusEvent(event.statusEvent), event.timestamp)
        }

        private fun toSvar(arbeidsgiverSporsmal: SporsmalOgSvarDTO, sykmeldingId: String): Svar {
            return Svar(
                    sykmeldingId,
                    sporsmalId = null,
                    svartype = toSvartype(arbeidsgiverSporsmal.svartype),
                    svar = arbeidsgiverSporsmal.svar)
        }

        private fun toSvartype(svartype: SvartypeDTO): Svartype {
            return when (svartype) {
                SvartypeDTO.ARBEIDSSITUASJON -> Svartype.ARBEIDSSITUASJON
                SvartypeDTO.PERIODER -> Svartype.PERIODER
                SvartypeDTO.JA_NEI -> Svartype.JA_NEI
            }
        }

        private fun toShortName(shortName: ShortNameDTO): ShortName {
            return when (shortName) {
                ShortNameDTO.ARBEIDSSITUASJON -> ShortName.ARBEIDSSITUASJON
                ShortNameDTO.NY_NARMESTE_LEDER -> ShortName.NY_NARMESTE_LEDER
                ShortNameDTO.FRAVAER -> ShortName.FRAVAER
                ShortNameDTO.PERIODE -> ShortName.PERIODE
                ShortNameDTO.FORSIKRING -> ShortName.FORSIKRING
            }
        }

        private fun toStatusEvent(statusEvent: StatusEventDTO): StatusEvent {
            return when (statusEvent) {
                StatusEventDTO.APEN -> StatusEvent.APEN
                StatusEventDTO.AVBRUTT -> StatusEvent.AVBRUTT
                StatusEventDTO.UTGATT -> StatusEvent.UTGATT
                StatusEventDTO.SENDT -> StatusEvent.SENDT
                StatusEventDTO.BEKREFTET -> StatusEvent.BEKREFTET
            }
        }
    }
}
