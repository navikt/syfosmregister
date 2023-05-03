package no.nav.syfo.identendring

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.error.PersonNotFoundException
import no.nav.syfo.util.util.Unbounded
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class PdlAktorConsumer(
    private val kafkaConsumerAiven: KafkaConsumer<String, GenericRecord>,
    private val applicationState: ApplicationState,
    private val aivenTopic: String,
    private val leaderElection: LeaderElection,
    private val identendringService: IdentendringService,
) {
    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_DURATION_SECONDS = 10L
    }

    @ExperimentalTime
    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    if (leaderElection.isLeader()) {
                        runConsumer()
                    } else {
                        delay(10L.seconds)
                    }
                } catch (ex: Exception) {
                    when (ex) {
                        is InactiveIdentException -> {
                            log.warn("New ident is inactive in PDL, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                        }
                        is PersonNotFoundException -> {
                            log.warn("Person not found in PDL, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                        }
                        else -> {
                            log.error("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                        }
                    }
                    kafkaConsumerAiven.unsubscribe()
                    delay(DELAY_ON_ERROR_SECONDS.seconds)
                }
            }
        }
    }

    private suspend fun runConsumer() {
        kafkaConsumerAiven.subscribe(listOf(aivenTopic))
        log.info("Starting consuming topic $aivenTopic")
        while (applicationState.ready && leaderElection.isLeader()) {
            withContext(Dispatchers.IO) {
                kafkaConsumerAiven.poll(Duration.ofSeconds(POLL_DURATION_SECONDS)).forEach {
                    if (it.value() != null) {
                        handleIdent(it)
                    }
                }
            }
        }
        kafkaConsumerAiven.unsubscribe()
    }

    private suspend fun handleIdent(it: ConsumerRecord<String, GenericRecord>) {
        identendringService.oppdaterIdent(it.value().toIdentListe())
    }
}

fun GenericRecord.toIdentListe(): List<Ident> {
    return (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Ident(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            },
        )
    }
}
