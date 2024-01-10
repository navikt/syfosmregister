package no.nav.syfo.sykmelding.kafka.producer

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>,
    private val topicName: String
) {
    fun send(sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO, fnr: String) {
        log.info("Sending status to kafka topic $topicName")
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
            kafkaProducer
                .send(
                    ProducerRecord(
                        topicName,
                        sykmeldingStatusKafkaEventDTO.sykmeldingId,
                        sykmeldingStatusKafkaMessageDTO
                    )
                )
                .get()
        } catch (e: Exception) {
            log.error(
                "Kunne ikke skrive til status-topic for sykmeldingid ${sykmeldingStatusKafkaEventDTO.sykmeldingId}: {}",
                e.message
            )
            throw e
        }
    }
}
