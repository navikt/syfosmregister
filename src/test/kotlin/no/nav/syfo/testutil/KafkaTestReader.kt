package no.nav.syfo.testutil

import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer

class KafkaTestReader<T> {
    fun getMessagesFromTopic(
        kafkaConsumer: KafkaConsumer<String, T>,
        messagesToRead: Int
    ): Map<String, T?> {
        val map = hashMapOf<String, T?>()
        var messages = 0
        while (messages < messagesToRead) {
            val records = kafkaConsumer.poll(Duration.ofMillis(100))
            records.forEach { map.put(it.key(), it.value()) }
            if (!records.isEmpty) {
                kafkaConsumer.commitSync()
            }
            messages += records.count()
        }
        return map
    }
}
