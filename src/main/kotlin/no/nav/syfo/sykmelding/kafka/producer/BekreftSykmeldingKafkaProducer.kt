package no.nav.syfo.sykmelding.kafka.producer

import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BekreftSykmeldingKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage?>, private val topic: String) {
    fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        kafkaProducer.send(ProducerRecord(topic, sykmeldingKafkaMessage.sykmelding.id, sykmeldingKafkaMessage))
    }

    fun tombstoneSykmelding(sykmeldingId: String) {
        log.info("Tombstone sykkmelding {}", sykmeldingId)
        kafkaProducer.send(ProducerRecord(topic, sykmeldingId, null))
    }
}
