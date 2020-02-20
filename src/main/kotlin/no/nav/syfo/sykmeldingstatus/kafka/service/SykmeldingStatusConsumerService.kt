package no.nav.syfo.sykmeldingstatus.kafka.service

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.service.KafkaModelMapper.Companion.toArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.service.KafkaModelMapper.Companion.toSporsmal
import no.nav.syfo.sykmeldingstatus.kafka.service.KafkaModelMapper.Companion.toSykmeldingStatusEvent
import no.nav.syfo.util.TimestampUtil.Companion.getAdjustedToLocalDateTime
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    private val sykmeldingStatusService: SykmeldingStatusService,
    private val sykmeldingStatusKafkaConsumer: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState
) {

    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumerService::class.java)
    }

    suspend fun start() {
        sykmeldingStatusKafkaConsumer.subscribe()
        while (applicationState.alive) {
            val kafkaEvents = sykmeldingStatusKafkaConsumer.poll()
            kafkaEvents
                    .asSequence()
                    .forEach(handleStatusEvent())
        }
    }

    private fun handleStatusEvent(): (SykmeldingStatusKafkaMessageDTO) -> Unit = { sykmeldingStatusKafkaMessage ->
        log.info("got status update from kafka topic, sykmeldingId: {}, status: {}", sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId, sykmeldingStatusKafkaMessage.event.statusEvent.name)
        when (sykmeldingStatusKafkaMessage.event.statusEvent) {
            StatusEventDTO.SENDT -> registrerSendt(sykmeldingStatusKafkaMessage)
            StatusEventDTO.BEKREFTET -> registrerBekreftet(sykmeldingStatusKafkaMessage)
            else -> registrerStatus(sykmeldingStatusKafkaMessage)
        }
    }

    private fun registrerBekreftet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        val sykmeldingBekreftEvent = SykmeldingBekreftEvent(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
                getAdjustedToLocalDateTime(sykmeldingStatusKafkaMessage.event.timestamp),
                sykmeldingStatusKafkaMessage.event.sporsmals?.map { toSporsmal(it, sykmeldingStatusKafkaMessage.event.sykmeldingId) }
        )

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    private fun registrerStatus(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent)
    }

    private fun registrerSendt(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val arbeidsgiver: ArbeidsgiverStatusDTO = sykmeldingStatusKafkaMessage.event.arbeidsgiver
                ?: throw IllegalArgumentException("Arbeidsgiver er ikke oppgitt")
        val arbeidsgiverSporsmal: SporsmalOgSvarDTO = sykmeldingStatusKafkaMessage.event.sporsmals?.first { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }
                ?: throw IllegalArgumentException("Ingen sporsmal funnet")
        val sykmeldingId = sykmeldingStatusKafkaMessage.event.sykmeldingId
        val timestamp = sykmeldingStatusKafkaMessage.event.timestamp
        val sykmeldingSendEvent = SykmeldingSendEvent(sykmeldingId,
                getAdjustedToLocalDateTime(timestamp),
                toArbeidsgiverStatus(sykmeldingId, arbeidsgiver),
                toSporsmal(arbeidsgiverSporsmal, sykmeldingId)
                )
        val sykmeldingStatusEvent = toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)

        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }
}
