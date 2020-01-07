package no.nav.syfo.rerunkafka.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.rerunkafka.api.RerunRequest
import no.nav.syfo.rerunkafka.database.erBehandlingsutfallLagret
import no.nav.syfo.rerunkafka.database.getSykmeldingerByIds
import no.nav.syfo.rerunkafka.database.opprettBehandlingsutfall
import no.nav.syfo.rerunkafka.kafka.RerunKafkaProducer
import org.slf4j.LoggerFactory

public data class RerunKafkaMessage(val receivedSykmelding: ReceivedSykmelding, val validationResult: ValidationResult)

class RerunKafkaService(private val database: DatabaseInterface, private val rerunKafkaProducer: RerunKafkaProducer) {

    private val log = LoggerFactory.getLogger(RerunKafkaService::class.java)

    fun rerun(rerunRequest: RerunRequest): List<ReceivedSykmelding> {
        log.info("Got list with {} sykmeldinger", rerunRequest.ids.size)
        val receivedSykmelding = database.getSykmeldingerByIds(rerunRequest.ids)
        log.info("Got {} sykmeldinger from database", receivedSykmelding.size)
        receivedSykmelding.forEach {
            publishToKafka(RerunKafkaMessage(receivedSykmelding = it, validationResult = rerunRequest.behandlingsutfall))
            if (database.erBehandlingsutfallLagret(it.sykmelding.id)) {
                no.nav.syfo.log.info(
                        "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen", it.sykmelding.id
                )
            } else {
                database.opprettBehandlingsutfall(
                        Behandlingsutfall(
                                id = it.sykmelding.id,
                                behandlingsutfall = rerunRequest.behandlingsutfall
                        )
                )
                no.nav.syfo.log.info("Behandlingsutfall lagret i databasen, sykmeldingId: {}", it.sykmelding.id)
            }
        }
        return receivedSykmelding
    }

    private fun publishToKafka(rerunKafkaMessage: RerunKafkaMessage) {
        val loggingMeta = LoggingMeta(
                mottakId = rerunKafkaMessage.receivedSykmelding.navLogId,
                orgNr = rerunKafkaMessage.receivedSykmelding.legekontorOrgNr,
                msgId = rerunKafkaMessage.receivedSykmelding.msgId,
                sykmeldingId = rerunKafkaMessage.receivedSykmelding.sykmelding.id
        )
        log.info("Publishing receivedSykmeling to reruntopic: {}", fields(loggingMeta))
        rerunKafkaProducer.publishToKafka(rerunKafkaMessage)
    }
}
