package no.nav.syfo.sykmelding.kafka.producer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

abstract class TombstoneKafkaProducer(
    protected val kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage?>,
    protected val topic: String
) {
    suspend fun tombstoneSykmelding(sykmeldingId: String) {
        withContext(Dispatchers.IO) {
            log.info("Tombstone sykmelding {}", sykmeldingId)
            try {
                kafkaProducer.send(ProducerRecord(topic, sykmeldingId, null)).get()
            } catch (e: Exception) {
                log.error(
                    "Kunne ikke skrive tombstone til bekreft-topic for sykmeldingid $sykmeldingId: {}",
                    e.message
                )
                throw e
            }
        }
    }
}
