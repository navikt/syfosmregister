package no.nav.syfo.sykmelding.kafka.producer

import no.nav.syfo.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingTombstoneProducer(private val tombstoneProducer: KafkaProducer<String, Any?>, private val topics: List<String>) {
    fun tombstoneSykmelding(sykmeldingId: String) {
        log.info("Tombstone sykmelding {}", sykmeldingId)
        try {
            topics.forEach { topic ->
                tombstoneProducer.send(ProducerRecord(topic, sykmeldingId, null)).get()
            }
        } catch (e: Exception) {
            log.error("Kunne ikke skrive tombstone til bekreft-topic for sykmeldingid $sykmeldingId: {}", e.message)
            throw e
        }
    }
}
