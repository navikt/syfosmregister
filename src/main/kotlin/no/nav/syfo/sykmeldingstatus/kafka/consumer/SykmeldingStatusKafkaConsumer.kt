package no.nav.syfo.sykmeldingstatus.kafka.consumer

import java.time.Duration
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.consumer.KafkaConsumer

class SykmeldingStatusKafkaConsumer(val kafkaConsumer: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>, val topics: List<String>) {
    fun subscribe() {
        kafkaConsumer.subscribe(topics)
    }

    fun poll(): List<SykmeldingStatusKafkaMessageDTO> {
        return kafkaConsumer.poll(Duration.ZERO).map { it.value() }
    }
}
