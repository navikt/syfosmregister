package no.nav.syfo.juridiskvurdering

import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class JuridiskVurderingKafkaConsumer(
    val kafkaConsumer: KafkaConsumer<String, String>,
    val juridiskVurderingDB: JuridiskVurderingDB,
    val topic: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(JuridiskVurderingKafkaConsumer::class.java)
    }

    suspend fun start() = coroutineScope {
        while (isActive) {
            try {
                startConsumeMessages()
            } catch (ex: Exception) {
                log.error("Error consuming juridisk vurdering topic, delaying 60seconds", ex)
                kafkaConsumer.unsubscribe()
                delay(60_000)
            }
        }
        log.info("Job stopped")
    }

    suspend fun startConsumeMessages() = coroutineScope {
        kafkaConsumer.subscribe(listOf(topic))
        while (isActive) {
            val records = kafkaConsumer.poll(10.seconds.toJavaDuration())
            processRecords(records)
        }
    }

    private fun processRecords(records: ConsumerRecords<String, String>) {
        records.forEach { record ->
            try {
                val juridiskVurderingResult =
                    objectMapper.readValue<JuridiskVurderingResult>(record.value())
                juridiskVurderingResult.juridiskeVurderinger
                    .filter {
                        it.juridiskHenvisning.lovverk == Lovverk.FOLKETRYGDLOVEN &&
                            it.juridiskHenvisning.paragraf == "8-7" &&
                            it.juridiskHenvisning.ledd == 2
                    }
                    .forEach { vurdering ->
                        juridiskVurderingDB.insertOrUpdate(
                            vurdering,
                            vurdering.input.toTilbakedateringInputs()
                        )
                    }
            } catch (ex: Exception) {
                log.error(
                    "Error processing record with offset: ${record.offset()}, on partition: ${record.partition()}",
                    ex
                )
                throw ex
            }
        }
    }
}
