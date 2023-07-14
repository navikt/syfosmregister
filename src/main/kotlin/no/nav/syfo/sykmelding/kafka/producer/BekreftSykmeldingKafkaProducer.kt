package no.nav.syfo.sykmelding.kafka.producer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class BekreftSykmeldingKafkaProducer(
    kafkaProducer: KafkaProducer<String, SykmeldingKafkaMessage?>,
    topic: String,
) : TombstoneKafkaProducer(kafkaProducer, topic) {
    suspend fun sendSykmelding(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        withContext(Dispatchers.IO) {
            try {
                kafkaProducer
                    .send(
                        ProducerRecord(
                            topic,
                            sykmeldingKafkaMessage.sykmelding.id,
                            sykmeldingKafkaMessage
                        )
                    )
                    .get()
            } catch (e: Exception) {
                log.error(
                    "Kunne ikke skrive til bekreft-topic for sykmeldingid ${sykmeldingKafkaMessage.sykmelding.id}: {}",
                    e.message,
                )
                throw e
            }
        }
    }
}
