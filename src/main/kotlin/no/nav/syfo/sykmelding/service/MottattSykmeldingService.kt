package no.nav.syfo.sykmelding.service

import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
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
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.util.mapToSykmeldingsopplysninger
import no.nav.syfo.util.TimestampUtil.Companion.getMinTime
import no.nav.syfo.wrapExceptions

class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val env: Environment,
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val mottattSykmeldingKafkaProducer: MottattSykmeldingKafkaProducer,
    private val mottattSykmeldingStatusService: MottattSykmeldingStatusService,
) {
    private suspend fun sendtToMottattSykmeldingTopic(receivedSykmelding: ReceivedSykmelding) {
        val sykmelding = receivedSykmelding.toArbeidsgiverSykmelding()
        val message =
            MottattSykmeldingKafkaMessage(
                sykmelding = sykmelding,
                kafkaMetadata =
                    KafkaMetadataDTO(
                        receivedSykmelding.msgId,
                        receivedSykmelding.mottattDato.atOffset(ZoneOffset.UTC),
                        receivedSykmelding.personNrPasient,
                        "syfosmregister",
                    ),
            )
        mottattSykmeldingKafkaProducer.sendMottattSykmelding(message)
    }

    suspend fun handleMessageSykmelding(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta,
        topic: String,
    ) =
        withContext(Dispatchers.IO) {
            wrapExceptions(loggingMeta) {
                log.info(
                    "Mottatt sykmelding SM2013 fra $topic, {}",
                    StructuredArguments.fields(loggingMeta)
                )
                val sykmeldingsopplysninger = mapToSykmeldingsopplysninger(receivedSykmelding)
                val sykmeldingsdokument =
                    Sykmeldingsdokument(
                        id = receivedSykmelding.sykmelding.id,
                        sykmelding = receivedSykmelding.sykmelding,
                    )

                if (
                    database.connection.erSykmeldingsopplysningerLagret(sykmeldingsopplysninger.id)
                ) {
                    SYKMELDING_DUPLIKAT_COUNTER.inc()
                    log.warn(
                        "Sykmelding med id {} allerede lagret i databasen, {}",
                        receivedSykmelding.sykmelding.id,
                        StructuredArguments.fields(loggingMeta)
                    )
                    database.updateMottattSykmelding(sykmeldingsopplysninger, sykmeldingsdokument)
                    mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                        sykmeldingId = sykmeldingsopplysninger.id,
                        fnr = sykmeldingsopplysninger.pasientFnr
                    )
                } else {
                    sykmeldingStatusKafkaProducer.send(
                        SykmeldingStatusKafkaEventDTO(
                            receivedSykmelding.sykmelding.id,
                            getMinTime(receivedSykmelding.mottattDato),
                            STATUS_APEN,
                        ),
                        receivedSykmelding.personNrPasient,
                    )

                    database.lagreMottattSykmelding(
                        sykmeldingsopplysninger,
                        sykmeldingsdokument,
                    )

                    log.info(
                        "Sykmelding SM2013 lagret i databasen, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                    MESSAGE_STORED_IN_DB_COUNTER.inc()
                }

                if (topic != env.avvistSykmeldingTopic) {
                    sendtToMottattSykmeldingTopic(receivedSykmelding)
                }
            }
        }
}
