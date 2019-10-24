package no.nav.syfo.rerunkafka.kafka

import no.nav.syfo.Environment
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class RerunKafkaProducer(val kafkaProducer: KafkaProducer<String, String>, val environment: Environment) {
    fun publishToKafka(receivedSykmelding: ReceivedSykmelding) {
        kafkaProducer.send(ProducerRecord(environment.kafkaRerunTopic, receivedSykmelding.sykmelding.id, objectMapper.writeValueAsString(receivedSykmelding)))
    }
}
