package no.nav.syfo.sykmelding.kafka.service

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    private val sykmeldingStatusKafkaConsumerAiven: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState,
    private val mottattSykmeldingStatusService: MottattSykmeldingStatusService
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
                sykmeldingStatusKafkaConsumerAiven.unsubscribe()
            }
            delay(delayStart)
        }
    }

    private suspend fun run() {
        sykmeldingStatusKafkaConsumerAiven.subscribe()
        while (applicationState.ready) {
            val kafkaEvents = sykmeldingStatusKafkaConsumerAiven.poll()
            kafkaEvents.forEach {
                mottattSykmeldingStatusService.handleStatusEvent(it, source = "aiven")
            }
            if (kafkaEvents.isNotEmpty()) {
                sykmeldingStatusKafkaConsumerAiven.commitSync()
            }
            delay(100)
        }
    }
}
