package no.nav.syfo.sykmelding.kafka.service

import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_UTGATT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent

class KafkaModelMapper private constructor() {
    companion object {
        fun toArbeidsgiverStatus(sykmeldingId: String, arbeidsgiver: ArbeidsgiverStatusDTO) =
                ArbeidsgiverStatus(sykmeldingId, arbeidsgiver.orgnummer, arbeidsgiver.juridiskOrgnummer, arbeidsgiver.orgNavn)

        fun toSporsmal(sporsmal: SporsmalOgSvarDTO, sykmeldingId: String): Sporsmal {
            return Sporsmal(sporsmal.tekst, toShortName(sporsmal.shortName), toSvar(sporsmal, sykmeldingId))
        }

        fun toSykmeldingStatusEvent(event: SykmeldingStatusKafkaEventDTO): SykmeldingStatusEvent {
            return SykmeldingStatusEvent(event.sykmeldingId, event.timestamp, toStatusEvent(event.statusEvent))
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

        private fun toStatusEvent(statusEvent: String): StatusEvent {
            return when (statusEvent) {
                STATUS_APEN -> StatusEvent.APEN
                STATUS_AVBRUTT -> StatusEvent.AVBRUTT
                STATUS_UTGATT -> StatusEvent.UTGATT
                STATUS_SENDT -> StatusEvent.SENDT
                STATUS_BEKREFTET -> StatusEvent.BEKREFTET
                STATUS_SLETTET -> StatusEvent.SLETTET
                else -> throw IllegalArgumentException("Unknown status")
            }
        }
    }
}
