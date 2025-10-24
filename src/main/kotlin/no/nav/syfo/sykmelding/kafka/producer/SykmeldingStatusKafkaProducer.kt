package no.nav.syfo.sykmelding.kafka.producer

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>,
    private val topicName: String
) {
    suspend fun send(sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO, fnr: String) {

        if (sykmeldingStatusKafkaEventDTO.statusEvent == STATUS_APEN) {
            val timestamp = sykmeldingStatusKafkaEventDTO.timestamp
            val timecutoff =
                OffsetDateTime.of(LocalDateTime.of(2025, 10, 24, 8, 35), ZoneOffset.UTC)
            if (timestamp.isAfter(timecutoff)) {
                log.info(
                    "Do not send ${sykmeldingStatusKafkaEventDTO.statusEvent} for sykmelding ${sykmeldingStatusKafkaEventDTO.sykmeldingId} since $timestamp is after $timecutoff"
                )
                return
            } else {
                log.info(
                    "Sending ${sykmeldingStatusKafkaEventDTO.statusEvent} for sykmelding ${sykmeldingStatusKafkaEventDTO.sykmeldingId} since $timestamp is before $timecutoff"
                )
            }
        }

        val sykmeldingStatusKafkaMessageDTO =
            SykmeldingStatusKafkaMessageDTO(
                KafkaMetadataDTO(
                    sykmeldingStatusKafkaEventDTO.sykmeldingId,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    fnr,
                    "syfosmregister"
                ),
                sykmeldingStatusKafkaEventDTO,
            )
        try {
            withContext(Dispatchers.IO) {
                kafkaProducer
                    .send(
                        ProducerRecord(
                            topicName,
                            sykmeldingStatusKafkaEventDTO.sykmeldingId,
                            sykmeldingStatusKafkaMessageDTO
                        )
                    )
                    .get()
            }
        } catch (e: Exception) {
            log.error(
                "Kunne ikke skrive til status-topic for sykmeldingid ${sykmeldingStatusKafkaEventDTO.sykmeldingId}: {}",
                e.message
            )
            throw e
        }
    }
}
