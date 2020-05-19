package no.nav.syfo.sykmelding.kafka.service

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper.Companion.toArbeidsgiverStatus
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper.Companion.toSporsmal
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper.Companion.toSykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    private val sykmeldingStatusService: SykmeldingStatusService,
    private val sykmeldingStatusKafkaConsumer: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState,
    private val sendtSykmeldingKafkaProducer: SendtSykmeldingKafkaProducer,
    private val bekreftetSykmeldingKafkaProducer: BekreftSykmeldingKafkaProducer
) {

    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumerService::class.java)
        private const val delayStart = 10_000L
    }

    private suspend fun run() {
        sykmeldingStatusKafkaConsumer.subscribe()
        while (applicationState.ready) {
            val kafkaEvents = sykmeldingStatusKafkaConsumer.poll()
            kafkaEvents
                    .forEach(handleStatusEvent())
            if (kafkaEvents.isNotEmpty()) {
                sykmeldingStatusKafkaConsumer.commitSync()
            }
            delay(100)
        }
    }

    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error("Error reading status from topic, trying again in {} milliseconds, error {}", delayStart, ex.message)
                sykmeldingStatusKafkaConsumer.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private fun handleStatusEvent(): (SykmeldingStatusKafkaMessageDTO) -> Unit = { sykmeldingStatusKafkaMessage ->
        log.info("Got status update from kafka topic, sykmeldingId: {}, status: {}", sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId, sykmeldingStatusKafkaMessage.event.statusEvent.name)
        try {
            when (sykmeldingStatusKafkaMessage.event.statusEvent) {
                StatusEventDTO.SENDT -> {
                    handleSendtSykmelding(sykmeldingStatusKafkaMessage)
                }
                StatusEventDTO.BEKREFTET -> {
                    registrerBekreftet(sykmeldingStatusKafkaMessage)
                    publishToBekreftSykmeldingTopic(sykmeldingStatusKafkaMessage)
                }
                else -> registrerStatus(sykmeldingStatusKafkaMessage)
            }
        } catch (e: Exception) {
            log.error("Kunne ikke prosessere statusendring {} for sykmeldingid {} fordi {}", sykmeldingStatusKafkaMessage.event.statusEvent.name, sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId, e.cause)
            throw e
        }
    }

    private fun handleSendtSykmelding(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val latestStatus = sykmeldingStatusService.getSykmeldingStatus(sykmeldingStatusKafkaMessage.event.sykmeldingId, null)
        if (latestStatus.any {
                    it.event == StatusEvent.SENDT
                }) {
            log.warn("Sykmelding er allerede sendt sykmeldingId {}", sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
            return
        }
        registrerSendt(sykmeldingStatusKafkaMessage)
        publishToSendtSykmeldingTopic(sykmeldingStatusKafkaMessage)
    }

    private fun publishToBekreftSykmeldingTopic(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        bekreftetSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private fun publishToSendtSykmeldingTopic(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        sendtSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private fun getKafkaMessage(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO): SykmeldingKafkaMessage {
        val sendtSykmelding = sykmeldingStatusService.getEnkelSykmelding(sykmeldingStatusKafkaMessage.event.sykmeldingId)
        val sendEvent = sykmeldingStatusKafkaMessage.event
        val metadata = sykmeldingStatusKafkaMessage.kafkaMetadata

        val sendtSykmeldingKafkaMessage = SykmeldingKafkaMessage(sendtSykmelding!!, metadata, sendEvent)
        return sendtSykmeldingKafkaMessage
    }

    private fun registrerBekreftet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        val sykmeldingBekreftEvent = SykmeldingBekreftEvent(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
                sykmeldingStatusKafkaMessage.event.timestamp,
                sykmeldingStatusKafkaMessage.event.sporsmals?.map { toSporsmal(it, sykmeldingStatusKafkaMessage.event.sykmeldingId) }
        )

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    private fun registrerStatus(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val lastStatus = sykmeldingStatusService.getSykmeldingStatus(sykmeldingsid = sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId, filter = "LATEST")
        if (lastStatus.any { it.event == StatusEvent.BEKREFTET }) {
            bekreftetSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingStatusKafkaMessage.event.sykmeldingId)
        }
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
                timestamp,
                toArbeidsgiverStatus(sykmeldingId, arbeidsgiver),
                toSporsmal(arbeidsgiverSporsmal, sykmeldingId)
        )
        val sykmeldingStatusEvent = toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)

        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }
}
