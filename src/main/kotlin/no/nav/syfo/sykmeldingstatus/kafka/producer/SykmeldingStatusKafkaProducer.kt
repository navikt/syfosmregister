package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>, private val topicName: String) {
    fun send(sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO) {
        val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(
                KafkaMetadataDTO(sykmeldingStatusKafkaEventDTO.sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC), "syfosmregister"),
                sykmeldingStatusKafkaEventDTO
        )
        kafkaProducer.send(ProducerRecord(topicName, sykmeldingStatusKafkaEventDTO.sykmeldingId, sykmeldingStatusKafkaMessageDTO))
    }
}
