package no.nav.syfo.sykmelding.kafka.service

import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService

class UpdateStatusService(private val sykmeldingStatusService: SykmeldingStatusService) {
    suspend fun handleStatusEvent(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        log.info("Got status update ${sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId}, status: ${sykmeldingStatusKafkaMessage.event.statusEvent}")
        when (sykmeldingStatusKafkaMessage.event.statusEvent) {
            STATUS_SENDT -> {
                updateSendt(sykmeldingStatusKafkaMessage)
            }
            STATUS_BEKREFTET -> {
                updateBekreftet(sykmeldingStatusKafkaMessage)
            }
            STATUS_SLETTET -> {
                updateSlettet(sykmeldingStatusKafkaMessage)
            }
            else -> {
                updateStatus(sykmeldingStatusKafkaMessage)
            }
        }
    }

    private suspend fun updateStatus(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent)
    }

    private suspend fun updateSlettet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        sykmeldingStatusService.slettSykmelding(sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
    }

    private suspend fun updateBekreftet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        val sykmeldingBekreftEvent = SykmeldingBekreftEvent(
            sykmeldingStatusKafkaMessage.event.sykmeldingId,
            sykmeldingStatusKafkaMessage.event.timestamp,
            sykmeldingStatusKafkaMessage.event.sporsmals?.map { KafkaModelMapper.toSporsmal(it, sykmeldingStatusKafkaMessage.event.sykmeldingId) }
        )

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    private suspend fun updateSendt(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val latestStatus = sykmeldingStatusService.getSykmeldingStatus(sykmeldingStatusKafkaMessage.event.sykmeldingId, null)
        if (latestStatus.any {
            it.event == StatusEvent.SENDT
        }
        ) {
            log.warn("Sykmelding er allerede sendt sykmeldingId {}", sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
            return
        }
        registrerSendt(sykmeldingStatusKafkaMessage)
    }

    private suspend fun registrerSendt(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val arbeidsgiver: ArbeidsgiverStatusDTO = sykmeldingStatusKafkaMessage.event.arbeidsgiver
            ?: throw IllegalArgumentException("Arbeidsgiver er ikke oppgitt")
        val arbeidsgiverSporsmal: SporsmalOgSvarDTO = sykmeldingStatusKafkaMessage.event.sporsmals?.first { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }
            ?: throw IllegalArgumentException("Ingen sporsmal funnet")
        val sykmeldingId = sykmeldingStatusKafkaMessage.event.sykmeldingId
        val timestamp = sykmeldingStatusKafkaMessage.event.timestamp
        val sykmeldingSendEvent = SykmeldingSendEvent(
            sykmeldingId,
            timestamp,
            KafkaModelMapper.toArbeidsgiverStatus(sykmeldingId, arbeidsgiver),
            KafkaModelMapper.toSporsmal(arbeidsgiverSporsmal, sykmeldingId)
        )
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)

        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }
}
