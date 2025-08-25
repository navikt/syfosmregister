package no.nav.syfo.sykmelding.service

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.ZoneOffset
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.SYKMELDING_DUPLIKAT_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.erBehandlingsutfallLagret
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.updateBehandlingsutfall
import no.nav.syfo.persistering.updateMottattSykmelding
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.util.mapToSykmeldingsopplysninger
import no.nav.syfo.util.TimestampUtil.Companion.getMinTime
import no.nav.syfo.wrapExceptions

class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val env: Environment,
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val mottattSykmeldingKafkaProducer: MottattSykmeldingKafkaProducer,
    private val mottattSykmeldingStatusService: MottattSykmeldingStatusService,
    private val sykmeldingStatusService: SykmeldingStatusService,
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

    @WithSpan
    suspend fun handleMessageSykmelding(
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta,
        topic: String,
    ) =
        wrapExceptions(loggingMeta) {
            Span.current().setAttribute("sykmeldingId", receivedSykmelding.sykmelding.id)

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
            val sykmeldingStatusKafkaEventDTO =
                SykmeldingStatusKafkaEventDTO(
                    receivedSykmelding.sykmelding.id,
                    getMinTime(receivedSykmelding.mottattDato),
                    STATUS_APEN,
                    brukerSvar = null
                )
            if (database.erSykmeldingsopplysningerLagret(sykmeldingsopplysninger.id)) {
                SYKMELDING_DUPLIKAT_COUNTER.inc()
                log.warn(
                    "Sykmelding med id {} allerede lagret i databasen, {}",
                    receivedSykmelding.sykmelding.id,
                    StructuredArguments.fields(loggingMeta)
                )
                insertOrUpdateBehandlingsutfall(
                    sykmeldingsopplysninger,
                    loggingMeta,
                    receivedSykmelding
                )
                sykmeldingStatusService.registrerStatus(
                    KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaEventDTO)
                )
                database.updateMottattSykmelding(sykmeldingsopplysninger, sykmeldingsdokument)
                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    sykmeldingId = sykmeldingsopplysninger.id,
                    fnr = sykmeldingsopplysninger.pasientFnr
                )
            } else {

                sykmeldingStatusService.registrerStatus(
                    KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaEventDTO)
                )
                sykmeldingStatusKafkaProducer.send(
                    sykmeldingStatusKafkaEventDTO,
                    receivedSykmelding.personNrPasient,
                )

                database.lagreMottattSykmelding(
                    sykmeldingsopplysninger,
                    sykmeldingsdokument,
                )
                insertOrUpdateBehandlingsutfall(
                    sykmeldingsopplysninger,
                    loggingMeta,
                    receivedSykmelding
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

    private suspend fun insertOrUpdateBehandlingsutfall(
        sykmeldingsopplysninger: Sykmeldingsopplysninger,
        loggingMeta: LoggingMeta,
        receivedSykmelding: ReceivedSykmelding
    ) {
        if (database.erBehandlingsutfallLagret(sykmeldingsopplysninger.id)) {
            log.warn(
                "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen, {}",
                sykmeldingsopplysninger.id,
                StructuredArguments.fields(loggingMeta),
            )
            database.updateBehandlingsutfall(
                Behandlingsutfall(
                    id = sykmeldingsopplysninger.id,
                    behandlingsutfall = receivedSykmelding.validationResult,
                ),
            )
        } else {
            database.opprettBehandlingsutfall(
                Behandlingsutfall(
                    id = sykmeldingsopplysninger.id,
                    behandlingsutfall = receivedSykmelding.validationResult,
                ),
            )
            log.info(
                "Behandlingsutfall lagret i databasen, {}",
                StructuredArguments.fields(loggingMeta),
            )
        }
    }
}
