package no.nav.syfo.sykmelding.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.LoggingMeta
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.SYKMELDING_DUPLIKAT_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.updateMottattSykmelding
import no.nav.syfo.sykmelding.util.mapToSykmeldingsopplysninger
import no.nav.syfo.wrapExceptions

class UpdateSykmeldingService(
    private val database: DatabaseInterface
) {
    suspend fun handleMessageSykmelding(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta,
        topic: String
    ) = withContext(Dispatchers.IO) {
        wrapExceptions(loggingMeta) {
            log.info("Mottatt sykmelding SM2013 fra $topic, {}", StructuredArguments.fields(loggingMeta))
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
