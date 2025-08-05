package no.nav.syfo.sykmelding.kafka.service

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    private val sykmeldingStatusKafkaConsumer: SykmeldingStatusKafkaConsumer,
    private val applicationState: ApplicationState,
    private val mottattSykmeldingStatusService: MottattSykmeldingStatusService,
) {

    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusConsumerService::class.java)
        private const val DELAY_START = 10_000L
    }

    suspend fun start() {
        while (applicationState.alive) {
            try {
                run()
            } catch (ex: Exception) {
                log.error(
                    "Error reading status from aiven topic, trying again in {} milliseconds, error {}",
                    DELAY_START,
                    ex.message,
                )
                sykmeldingStatusKafkaConsumer.unsubscribe()
            }
            delay(DELAY_START)
        }
    }

    private suspend fun run() {
        sykmeldingStatusKafkaConsumer.subscribe()
        while (applicationState.ready) {

            val kafkaEvents = sykmeldingStatusKafkaConsumer.poll()
            kafkaEvents.forEach { handleStatusEvent(it) }
            if (kafkaEvents.isNotEmpty()) {

                sykmeldingStatusKafkaConsumer.commitSync()
            }
        }
    }

    @WithSpan
    private suspend fun handleStatusEvent(it: SykmeldingStatusKafkaMessageDTO) {
        log.info("Mottatt sykmelding status ${it.event.sykmeldingId}")
        mottattSykmeldingStatusService.handleStatusEvent(it)
    }
}
