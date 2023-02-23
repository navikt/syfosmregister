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
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
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
            ArbeidsgiverStatus(
                sykmeldingId,
                arbeidsgiver.orgnummer,
                arbeidsgiver.juridiskOrgnummer,
                arbeidsgiver.orgNavn
            )

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
                svar = arbeidsgiverSporsmal.svar
            )
        }

        fun toSykmeldingStatusKafkaEventDTO(
            status: SykmeldingStatusEvent,
            arbeidsgiverStatus: ArbeidsgiverDbModel?,
            sporsmal: List<Sporsmal>
        ) = SykmeldingStatusKafkaEventDTO(
            sykmeldingId = status.sykmeldingId,
            timestamp = status.timestamp,
            statusEvent = status.event.name,
            arbeidsgiver = toArbeidsgiverStatusDto(arbeidsgiverStatus),
            sporsmals = sporsmal.map { toSporsmalOgSvar(it) }
        )

        private fun toSvartype(svartype: SvartypeDTO): Svartype {
            return when (svartype) {
                SvartypeDTO.ARBEIDSSITUASJON -> Svartype.ARBEIDSSITUASJON
                SvartypeDTO.PERIODER -> Svartype.PERIODER
                SvartypeDTO.JA_NEI -> Svartype.JA_NEI
                SvartypeDTO.DAGER -> Svartype.DAGER
            }
        }

        private fun toShortName(shortName: ShortNameDTO): ShortName {
            return when (shortName) {
                ShortNameDTO.ARBEIDSSITUASJON -> ShortName.ARBEIDSSITUASJON
                ShortNameDTO.NY_NARMESTE_LEDER -> ShortName.NY_NARMESTE_LEDER
                ShortNameDTO.FRAVAER -> ShortName.FRAVAER
                ShortNameDTO.PERIODE -> ShortName.PERIODE
                ShortNameDTO.FORSIKRING -> ShortName.FORSIKRING
                ShortNameDTO.EGENMELDINGSDAGER -> ShortName.EGENMELDINGSDAGER
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

        fun toArbeidsgiverStatusDto(it: ArbeidsgiverDbModel?): ArbeidsgiverStatusDTO? {
            return it?.let {
                ArbeidsgiverStatusDTO(
                    orgnummer = it.orgnummer,
                    juridiskOrgnummer = it.juridiskOrgnummer,
                    orgNavn = it.orgNavn
                )
            }
        }

        fun toSporsmalOgSvar(it: Sporsmal): SporsmalOgSvarDTO {
            return SporsmalOgSvarDTO(
                tekst = it.tekst,
                shortName = toShortNameDto(it.shortName),
                svartype = toSvartypeDto(it.svar.svartype),
                svar = it.svar.svar
            )
        }

        private fun toSvartypeDto(svartype: Svartype): SvartypeDTO {
            return when (svartype) {
                Svartype.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
                Svartype.PERIODER -> SvartypeDTO.PERIODER
                Svartype.JA_NEI -> SvartypeDTO.JA_NEI
                Svartype.DAGER -> SvartypeDTO.DAGER
            }
        }

        private fun toShortNameDto(shortName: ShortName): ShortNameDTO {
            return when (shortName) {
                ShortName.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
                ShortName.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
                ShortName.FRAVAER -> ShortNameDTO.FRAVAER
                ShortName.PERIODE -> ShortNameDTO.PERIODE
                ShortName.FORSIKRING -> ShortNameDTO.FORSIKRING
                ShortName.EGENMELDINGSDAGER -> ShortNameDTO.EGENMELDINGSDAGER
            }
        }
    }
}
