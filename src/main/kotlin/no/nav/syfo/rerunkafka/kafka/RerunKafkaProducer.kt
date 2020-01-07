package no.nav.syfo.rerunkafka.kafka

import no.nav.syfo.Environment
import no.nav.syfo.objectMapper
import no.nav.syfo.rerunkafka.service.RerunKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class RerunKafkaProducer(val kafkaProducer: KafkaProducer<String, String>, val environment: Environment) {
    fun publishToKafka(RerunKafkaMessage: RerunKafkaMessage) {
        kafkaProducer.send(ProducerRecord(environment.kafkaRerunTopic, RerunKafkaMessage.receivedSykmelding.sykmelding.id, objectMapper.writeValueAsString(RerunKafkaMessage)))
    }
}
