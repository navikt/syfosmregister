package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.LocalDateTime
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.kafka.model.KafkaMetadata
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessage>, private val topicName: String) {
    fun send(sykmeldingStatusKafkaEvent: SykmeldingStatusKafkaEvent) {
        log.info("Skriver statusendring for sykmelding med id {} til topic", sykmeldingStatusKafkaEvent.sykmeldingId)
        val metadata = KafkaMetadata(sykmeldingStatusKafkaEvent.sykmeldingId, LocalDateTime.now())
        val sykmeldingStatusKafkaMessage = SykmeldingStatusKafkaMessage(metadata, sykmeldingStatusKafkaEvent)
        kafkaProducer.send(ProducerRecord(topicName, sykmeldingStatusKafkaMessage.event.sykmeldingId, sykmeldingStatusKafkaMessage))
    }
}
