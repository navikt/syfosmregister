package no.nav.syfo.sykmelding.kafka.service

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.service.MottattSykmeldingConsumerService
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    private val sykmeldingStatusKafkaConsumer: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState,
    private val mottattSykmeldingStatusService: MottattSykmeldingStatusService,
    private val updateStatusService: UpdateStatusService
) {

    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumerService::class.java)
        private const val delayStart = 10_000L
    }

    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error("Error reading status from aiven topic, trying again in {} milliseconds, error {}", delayStart, ex.message)
                sykmeldingStatusKafkaConsumer.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private suspend fun run() {
        sykmeldingStatusKafkaConsumer.subscribe()
        while (applicationState.ready) {
            val kafkaEvents = sykmeldingStatusKafkaConsumer.poll()
            kafkaEvents.forEach {
                handleStatusEvent(it)
            }
            if (kafkaEvents.isNotEmpty()) {
                sykmeldingStatusKafkaConsumer.commitSync()
            }
            delay(100)
        }
    }

    private suspend fun handleStatusEvent(it: SykmeldingStatusKafkaMessageDTO) {
        val kafkaMessageTimestamp = it.event.timestamp
        when (kafkaMessageTimestamp.isAfter(MottattSykmeldingConsumerService.CHANGE_TIMESTAMP)) {
            true -> {
                log.info("Mottatt sykmelding status ${it.event.sykmeldingId} er etter tidspunkt for bytting av logikk")
                mottattSykmeldingStatusService.handleStatusEvent(it)
            }
            else -> {
                log.info("Mottatt sykmelding status ${it.event.sykmeldingId} er før bytting, lagrer bare i DB")
                updateStatusService.handleStatusEvent(it)
            }
        }
    }
}
