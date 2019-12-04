package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.kafka.model.KafkaMetadata
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessage>, val topicName: String) {
    fun send(sykmeldingStatusEvent: SykmeldingStatusKafkaEvent, source: String) {
        val metadata = KafkaMetadata(sykmeldingStatusEvent.sykmeldingId, source, LocalDateTime.now())
        val sykmeldingStatusKafkaEvent = SykmeldingStatusKafkaEvent(
                sykmeldingStatusEvent.sykmeldingId,
                sykmeldingStatusEvent.timestamp,
                sykmeldingStatusEvent.statusEvent,
                sykmeldingStatusEvent.arbeidsgiver,
                sykmeldingStatusEvent.sporsmals

        )
        val sykmeldingStatusKafkaMessage = SykmeldingStatusKafkaMessage(metadata, sykmeldingStatusKafkaEvent)
        kafkaProducer.send(ProducerRecord(topicName, sykmeldingStatusKafkaMessage.event.sykmeldingId.toString(), sykmeldingStatusKafkaMessage))
    }
}
