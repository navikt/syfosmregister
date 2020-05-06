package no.nav.syfo.sykmelding.kafka.consumer

import java.time.Duration
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.consumer.KafkaConsumer

class SykmeldingStatusKafkaConsumer(private val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>, val topics: List<String>) {
    fun subscribe() {
        kafkaConsumer.subscribe(topics)
    }

    fun poll(): List<SykmeldingStatusKafkaMessageDTO> {
        return kafkaConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }

    fun commitSync() {
        kafkaConsumer.commitSync()
    }

    fun unsubscribe() {
        kafkaConsumer.unsubscribe()
    }
}
