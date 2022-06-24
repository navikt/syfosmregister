package no.nav.syfo.sykmelding.kafka.producer

import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BekreftSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage?>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage)).get()
        } catch (e: Exception) {
            log.error("Kunne ikke skrive til bekreft-topic for sykmeldingid ${sykmeldingKafkaMessage.sykmelding.id}: {}", e.message)
            throw e
        }
    }

    fun tombstoneSykmelding(sykmeldingId: String) {
        log.info("Tombstone sykmelding {}", sykmeldingId)
        try {
            kafkaProducer.send(ProducerRecord(topic, sykmeldingId, null)).get()
        } catch (e: Exception) {
            log.error("Kunne ikke skrive tombstone til bekreft-topic for sykmeldingid $sykmeldingId: {}", e.message)
            throw e
        }
    }
}
