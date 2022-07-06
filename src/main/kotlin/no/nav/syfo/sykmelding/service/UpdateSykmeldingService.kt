package no.nav.syfo.sykmelding.service

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.SYKMELDING_DUPLIKAT_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.updateMottattSykmelding
import no.nav.syfo.sykmelding.util.mapToSykmeldingsopplysninger
import no.nav.syfo.wrapExceptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class UpdateSykmeldingService(
    private val applicationState: ApplicationState,
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val database: DatabaseInterface,
    private val env: Environment
) {
    suspend fun start() {
        kafkaAivenConsumer.subscribe(
            listOf(
                env.okSykmeldingTopic,
                env.manuellSykmeldingTopic,
                env.avvistSykmeldingTopic
            )
        )
        while (applicationState.ready) {
            kafkaAivenConsumer.poll(Duration.ofMillis(0)).filterNot { it.value() == null }.forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
                )
                handleMessageSykmelding(receivedSykmelding, database, loggingMeta, "aiven")
            }
            delay(100)
        }
    }

    private suspend fun handleMessageSykmelding(
        receivedSykmelding: ReceivedSykmelding,
        database: DatabaseInterface,
        loggingMeta: LoggingMeta,
        source: String
    ) = withContext(Dispatchers.IO) {
        wrapExceptions(loggingMeta) {
            log.info("Mottatt sykmelding SM2013 fra $source, {}", StructuredArguments.fields(loggingMeta))
            val sykmeldingsopplysninger = mapToSykmeldingsopplysninger(receivedSykmelding)
            val sykmeldingsdokument = Sykmeldingsdokument(
                id = receivedSykmelding.sykmelding.id,
                sykmelding = receivedSykmelding.sykmelding
            )

            if (database.connection.erSykmeldingsopplysningerLagret(sykmeldingsopplysninger.id)) {
                SYKMELDING_DUPLIKAT_COUNTER.inc()
                log.warn(
                    "Sykmelding med id {} allerede lagret i databasen, {}",
                    receivedSykmelding.sykmelding.id,
                    StructuredArguments.fields(loggingMeta)
                )
                database.updateMottattSykmelding(sykmeldingsopplysninger, sykmeldingsdokument)
            } else {
                database.lagreMottattSykmelding(
                    sykmeldingsopplysninger,
                    sykmeldingsdokument
                )
                log.info("Sykmelding SM2013 lagret i databasen, {}", StructuredArguments.fields(loggingMeta))
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }
}
