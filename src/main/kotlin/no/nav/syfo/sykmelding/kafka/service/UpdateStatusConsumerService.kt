package no.nav.syfo.sykmelding.kafka.service

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import org.slf4j.LoggerFactory

class UpdateStatusConsumerService(
    private val statusConsumer: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState,
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
                statusConsumer.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private suspend fun run() {
        statusConsumer.subscribe()
        while (applicationState.ready) {
            val kafkaEvents = statusConsumer.poll()
            kafkaEvents.forEach {
                updateStatusService.handleStatusEvent(it)
            }
            if (kafkaEvents.isNotEmpty()) {
                statusConsumer.commitSync()
            }
            delay(100)
        }
    }
}
