package no.nav.syfo.juridiskvurdering

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

@OptIn(DelicateCoroutinesApi::class)
fun Application.createAndStartConsumer(
    env: Environment,
    databaseInterface: DatabaseInterface,
    applicationState: ApplicationState
) {

    val kafkaConfig =
        KafkaUtils.getAivenKafkaConfig("juridisk-consumer").apply {
            this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1000"
            this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
    val kafkaConsumer =
        KafkaConsumer(
            kafkaConfig.toConsumerConfig("juridisk-vurdering-consumer", StringDeserializer::class),
            StringDeserializer(),
            StringDeserializer()
        )

    val juridiskVurderingKafkaConsumer =
        JuridiskVurderingKafkaConsumer(
            kafkaConsumer,
            JuridiskVurderingDB(databaseInterface),
            env.juridiskVurderingTopic
        )

    val job = GlobalScope.launch(Dispatchers.IO) { juridiskVurderingKafkaConsumer.start() }

    environment.monitor.subscribe(ApplicationStopping) { job.cancel() }
}
