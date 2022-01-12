package no.nav.syfo.sykmelding.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.erBehandlingsutfallLagret
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.updateBehandlingsutfall
import no.nav.syfo.wrapExceptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class BehandlingsutfallService(
    private val applicationState: ApplicationState,
    private val kafkaconsumer: KafkaConsumer<String, String>,
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val database: DatabaseInterface,
    private val env: Environment
) {

    suspend fun start() {
        kafkaconsumer.subscribe(
            listOf(
                env.sm2013BehandlingsUtfallTopic
            )
        )
        while (applicationState.ready) {
            kafkaconsumer.poll(Duration.ofMillis(0)).filterNot { it.value() == null }.forEach {
                val sykmeldingsid = it.key()
                val validationResult: ValidationResult = objectMapper.readValue(it.value())
                val loggingMeta = LoggingMeta(
                    mottakId = "",
                    orgNr = "",
                    msgId = "",
                    sykmeldingId = sykmeldingsid
                )
                handleMessageBehandlingsutfall(validationResult, sykmeldingsid, database, loggingMeta, "on-prem")
            }
            delay(100)
        }
    }

    suspend fun startAivenConsumer() {
        kafkaAivenConsumer.subscribe(
            listOf(
                env.behandlingsUtfallTopic
            )
        )
        while (applicationState.ready) {
            kafkaAivenConsumer.poll(Duration.ofMillis(0)).filterNot { it.value() == null }.forEach {
                val sykmeldingsid = it.key()
                val validationResult: ValidationResult = objectMapper.readValue(it.value())
                val loggingMeta = LoggingMeta(
                    mottakId = "",
                    orgNr = "",
                    msgId = "",
                    sykmeldingId = sykmeldingsid
                )
                handleMessageBehandlingsutfall(validationResult, sykmeldingsid, database, loggingMeta, "aiven")
            }
            delay(100)
        }
    }

    private suspend fun handleMessageBehandlingsutfall(
        validationResult: ValidationResult,
        sykmeldingsid: String,
        database: DatabaseInterface,
        loggingMeta: LoggingMeta,
        source: String
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottatt behandlingsutfall fra $source, {}", StructuredArguments.fields(loggingMeta))

            if (database.connection.erBehandlingsutfallLagret(sykmeldingsid)) {
                log.warn(
                    "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen, {}", StructuredArguments.fields(loggingMeta)
                )
                database.connection.updateBehandlingsutfall(Behandlingsutfall(id = sykmeldingsid, behandlingsutfall = validationResult))
            } else {
                database.connection.opprettBehandlingsutfall(
                    Behandlingsutfall(
                        id = sykmeldingsid,
                        behandlingsutfall = validationResult
                    )
                )
                log.info("Behandlingsutfall lagret i databasen, {}", StructuredArguments.fields(loggingMeta))
            }
        }
    }
}
