package no.nav.syfo.sykmelding.kafka.consumer

import java.time.Duration
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.consumer.KafkaConsumer

class SykmeldingStatusKafkaConsumer(
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    private val topics: List<String>
) {
    fun subscribe() {
        kafkaConsumer.subscribe(topics)
    }

    fun poll(): List<Pair<String, SykmeldingStatusKafkaMessageDTO?>> {
        return kafkaConsumer.poll(Duration.ofMillis(10_000)).map { it.key() to it.value() }
    }

    fun commitSync() {
        kafkaConsumer.commitSync()
    }

    fun unsubscribe() {
        kafkaConsumer.unsubscribe()
    }
}
