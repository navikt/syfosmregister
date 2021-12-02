package no.nav.syfo.sykmelding.kafka.consumer

import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class SykmeldingStatusKafkaConsumer(private val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>, val topics: List<String>) {
    fun subscribe() {
        kafkaConsumer.subscribe(topics)
    }

    fun poll(): List<SykmeldingStatusKafkaMessageDTO> {
        return kafkaConsumer.poll(Duration.ofMillis(0)).mapNotNull { it.value() }
    }

    fun commitSync() {
        kafkaConsumer.commitSync()
    }

    fun unsubscribe() {
        kafkaConsumer.unsubscribe()
    }
}
