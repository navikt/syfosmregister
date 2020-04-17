package no.nav.syfo.sykmeldingstatus.kafka.producer

import no.nav.syfo.sykmeldingstatus.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SendtSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SendtSykmeldingKafkaMessage>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SendtSykmeldingKafkaMessage) {
        kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage))
    }
}
