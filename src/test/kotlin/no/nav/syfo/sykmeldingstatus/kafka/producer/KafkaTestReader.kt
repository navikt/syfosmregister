package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.Duration
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer

fun getMessagesFromTopic(kafkaConsumer: KafkaConsumer<String, SykmeldingKafkaMessage>, messagesToRead: Int): Map<String, SykmeldingKafkaMessage?> {
    val map = hashMapOf<String, SykmeldingKafkaMessage?>()
    var messages = 0
    while (messages < messagesToRead) {
        val records = kafkaConsumer.poll(Duration.ofMillis(100))
        records.forEach {
            map.put(it.key(), it.value())
        }
        if (!records.isEmpty) {
            kafkaConsumer.commitSync()
        }
        messages += records.count()
    }
    return map
}
