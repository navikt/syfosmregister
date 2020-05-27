package no.nav.syfo.sykmelding.kafka.producer

import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SendtSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Kunne ikke skrive til sendt-topic for sykmeldingid ${sykmeldingKafkaMessage.sykmelding.id}: {}", e.message)
            throw e
        }
    }
}
