package no.nav.syfo.rerunkafka.service

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.rerunkafka.database.getSykmeldingerByIds
import no.nav.syfo.rerunkafka.kafka.RerunKafkaProducer
import org.slf4j.LoggerFactory

class RerunKafkaService(private val databaseInterface: DatabaseInterface, private val rerunKafkaProducer: RerunKafkaProducer) {

    private val log = LoggerFactory.getLogger(RerunKafkaService::class.java)

    fun rerun(sykmeldingIds: List<String>): List<ReceivedSykmelding> {
        log.info("Got list with {} sykmeldinger", sykmeldingIds.size)
        val receivedSykmelding = databaseInterface.getSykmeldingerByIds(sykmeldingIds)
        log.info("Got {} sykmeldinger from database", receivedSykmelding.size)
        receivedSykmelding.forEach {
            publishToKafka(it)
        }
        return receivedSykmelding
    }

    private fun publishToKafka(receivedSykmelding: ReceivedSykmelding) {
        val loggingMeta = LoggingMeta(
                mottakId = receivedSykmelding.navLogId,
                orgNr = receivedSykmelding.legekontorOrgNr,
                msgId = receivedSykmelding.msgId,
                sykmeldingId = receivedSykmelding.sykmelding.id
        )
        log.info("Publishing receivedSykmeling to reruntopic: {}", fields(loggingMeta))
        rerunKafkaProducer.publishToKafka(receivedSykmelding)
    }
}
