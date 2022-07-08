package no.nav.syfo.sykmelding.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MottattSykmeldingConsumerService(
    private val applicationState: ApplicationState,
    private val env: Environment,
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val updateSykmeldingService: UpdateSykmeldingService,
    private val mottattSykmeldingService: MottattSykmeldingService
) {

    companion object {
        val CHANGE_TIMESTAMP: OffsetDateTime = OffsetDateTime.of(2022, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC)
    }

    suspend fun start() {
        kafkaAivenConsumer.subscribe(
            listOf(
                env.okSykmeldingTopic,
                env.manuellSykmeldingTopic,
                env.avvistSykmeldingTopic
            )
        )
        while (applicationState.ready) {
            kafkaAivenConsumer.poll(Duration.ofMillis(0)).filterNot { it.value() == null }.forEach {
                handleMessageSykmelding(it)
            }
            delay(100)
        }
    }

    private suspend fun handleMessageSykmelding(it: ConsumerRecord<String, String>) {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
        val loggingMeta = LoggingMeta(
            mottakId = receivedSykmelding.navLogId,
            orgNr = receivedSykmelding.legekontorOrgNr,
            msgId = receivedSykmelding.msgId,
            sykmeldingId = receivedSykmelding.sykmelding.id
        )
        val kafkaMessageTimestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp()), ZoneOffset.UTC)
        when (kafkaMessageTimestamp.isAfter(CHANGE_TIMESTAMP)) {
            true -> {
                log.info("Mottatt sykmelding ${receivedSykmelding.sykmelding.id} er etter tidspunkt for bytting av logikk", loggingMeta)
                mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, it.topic())
            }
            else -> {
                log.info("Mottatt sykmelding ${receivedSykmelding.sykmelding.id} er før bytting, lagrer bare i DB", loggingMeta)
                updateSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, it.topic())
            }
        }
    }
}
