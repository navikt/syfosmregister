package no.nav.syfo.sykmeldingstatus.kafka.consumer

import java.time.Duration
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer

class SykmeldingStatusKafkaConsumer(val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessage>, val topics: List<String>) {
    fun poll(): List<SykmeldingStatusKafkaMessage> {
        return kafkaConsumer.poll(Duration.ZERO).map { it.value() }
    }

    fun subscribe() {
        kafkaConsumer.subscribe(topics)
    }
}
