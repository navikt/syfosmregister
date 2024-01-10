package no.nav.syfo.sykmelding.kafka.service

import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.STATUS_AVBRUTT
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.STATUS_SLETTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_UTGATT
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmelding.status.*

class KafkaModelMapper private constructor() {
    companion object {
        fun toArbeidsgiverStatus(sykmeldingId: String, arbeidsgiver: ArbeidsgiverStatusKafkaDTO) =
            ArbeidsgiverStatus(
                sykmeldingId,
                arbeidsgiver.orgnummer,
                arbeidsgiver.juridiskOrgnummer,
                arbeidsgiver.orgNavn,
            )

        fun toSporsmal(sporsmal: SporsmalOgSvarKafkaDTO, sykmeldingId: String): Sporsmal {
            return Sporsmal(
                sporsmal.tekst,
                toShortName(sporsmal.shortName),
                toSvar(sporsmal, sykmeldingId)
            )
        }

        fun toSykmeldingStatusEvent(event: SykmeldingStatusKafkaEventDTO): SykmeldingStatusEvent {
            return SykmeldingStatusEvent(
                event.sykmeldingId,
                event.timestamp,
                toStatusEvent(event.statusEvent)
            )
        }

        private fun toSvar(
            arbeidsgiverSporsmal: SporsmalOgSvarKafkaDTO,
            sykmeldingId: String
        ): Svar {
            return Svar(
                sykmeldingId,
                sporsmalId = null,
                svartype = toSvartype(arbeidsgiverSporsmal.svartype),
                svar = arbeidsgiverSporsmal.svar,
            )
        }

        fun toSykmeldingStatusKafkaEventDTO(
            status: SykmeldingStatusEvent,
            arbeidsgiverStatus: ArbeidsgiverDbModel?,
            sporsmal: List<Sporsmal>,
            tidligereArbeidsgiver: TidligereArbeidsgiverDTO?
        ) =
            SykmeldingStatusKafkaEventDTO(
                sykmeldingId = status.sykmeldingId,
                timestamp = status.timestamp,
                statusEvent = status.event.name,
                arbeidsgiver = toArbeidsgiverStatusDto(arbeidsgiverStatus),
                sporsmals = sporsmal.map { toSporsmalOgSvar(it) },
                tidligereArbeidsgiver =
                    tidligereArbeidsgiver?.let {
                        TidligereArbeidsgiverKafkaDTO(
                            orgNavn = it.orgNavn,
                            orgnummer = it.orgnummer,
                            sykmeldingsId = it.sykmeldingsId
                        )
                    }
            )

        private fun toSvartype(svartype: SvartypeKafkaDTO): Svartype {
            return when (svartype) {
                SvartypeKafkaDTO.ARBEIDSSITUASJON -> Svartype.ARBEIDSSITUASJON
                SvartypeKafkaDTO.PERIODER -> Svartype.PERIODER
                SvartypeKafkaDTO.JA_NEI -> Svartype.JA_NEI
                SvartypeKafkaDTO.DAGER -> Svartype.DAGER
            }
        }

        private fun toShortName(shortName: ShortNameKafkaDTO): ShortName {
            return when (shortName) {
                ShortNameKafkaDTO.ARBEIDSSITUASJON -> ShortName.ARBEIDSSITUASJON
                ShortNameKafkaDTO.NY_NARMESTE_LEDER -> ShortName.NY_NARMESTE_LEDER
                ShortNameKafkaDTO.FRAVAER -> ShortName.FRAVAER
                ShortNameKafkaDTO.PERIODE -> ShortName.PERIODE
                ShortNameKafkaDTO.FORSIKRING -> ShortName.FORSIKRING
                ShortNameKafkaDTO.EGENMELDINGSDAGER -> ShortName.EGENMELDINGSDAGER
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

        private fun toArbeidsgiverStatusDto(it: ArbeidsgiverDbModel?): ArbeidsgiverStatusKafkaDTO? {
            return it?.let {
                ArbeidsgiverStatusKafkaDTO(
                    orgnummer = it.orgnummer,
                    juridiskOrgnummer = it.juridiskOrgnummer,
                    orgNavn = it.orgNavn,
                )
            }
        }

        private fun toSporsmalOgSvar(it: Sporsmal): SporsmalOgSvarKafkaDTO {
            return SporsmalOgSvarKafkaDTO(
                tekst = it.tekst,
                shortName = toShortNameDto(it.shortName),
                svartype = toSvartypeDto(it.svar.svartype),
                svar = it.svar.svar,
            )
        }

        private fun toSvartypeDto(svartype: Svartype): SvartypeKafkaDTO {
            return when (svartype) {
                Svartype.ARBEIDSSITUASJON -> SvartypeKafkaDTO.ARBEIDSSITUASJON
                Svartype.PERIODER -> SvartypeKafkaDTO.PERIODER
                Svartype.JA_NEI -> SvartypeKafkaDTO.JA_NEI
                Svartype.DAGER -> SvartypeKafkaDTO.DAGER
            }
        }

        private fun toShortNameDto(shortName: ShortName): ShortNameKafkaDTO {
            return when (shortName) {
                ShortName.ARBEIDSSITUASJON -> ShortNameKafkaDTO.ARBEIDSSITUASJON
                ShortName.NY_NARMESTE_LEDER -> ShortNameKafkaDTO.NY_NARMESTE_LEDER
                ShortName.FRAVAER -> ShortNameKafkaDTO.FRAVAER
                ShortName.PERIODE -> ShortNameKafkaDTO.PERIODE
                ShortName.FORSIKRING -> ShortNameKafkaDTO.FORSIKRING
                ShortName.EGENMELDINGSDAGER -> ShortNameKafkaDTO.EGENMELDINGSDAGER
            }
        }
    }
}
