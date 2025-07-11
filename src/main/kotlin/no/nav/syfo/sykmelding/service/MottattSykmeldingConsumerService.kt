package no.nav.syfo.sykmelding.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.Duration
import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

class MottattSykmeldingConsumerService(
    private val applicationState: ApplicationState,
    private val env: Environment,
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val mottattSykmeldingService: MottattSykmeldingService,
) {
    companion object {
        private val DELAY_START = 10_000L
    }

    suspend fun start() {
        while (applicationState.alive) {
            try {
                kafkaAivenConsumer.subscribe(
                    listOf(
                        env.okSykmeldingTopic,
                        env.manuellSykmeldingTopic,
                        env.avvistSykmeldingTopic,
                    ),
                )
                run()
            } catch (ex: Exception) {
                log.error(
                    "Error reading sykmelding from topic, trying again in $DELAY_START milliseconds",
                    ex,
                )
                kafkaAivenConsumer.unsubscribe()
                delay(DELAY_START)
            }
        }
    }

    private suspend fun run() {
        while (applicationState.ready) {
            kafkaAivenConsumer
                .poll(Duration.ofMillis(1000))
                .filterNot { it.value() == null }
                .forEach { handleMessageSykmelding(it) }
        }
    }

    @WithSpan
    private suspend fun handleMessageSykmelding(it: ConsumerRecord<String, String>) {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
        val loggingMeta =
            LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id,
            )
        mottattSykmeldingService.handleMessageSykmelding(
            receivedSykmelding,
            loggingMeta,
            it.topic()
        )
    }
}
