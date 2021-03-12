package no.nav.syfo.sykmelding.service

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.SYKMELDING_DUPLIKAT_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.sykmelding.kafka.model.MottattSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.wrapExceptions
import org.apache.kafka.clients.consumer.KafkaConsumer

class MottattSykmeldingService(
    private val applicationState: ApplicationState,
    private val env: Environment,
    private val kafkaconsumer: KafkaConsumer<String, String>,
    private val database: DatabaseInterface,
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val mottattSykmeldingKafkaProducer: MottattSykmeldingKafkaProducer
) {

    suspend fun start() {
        kafkaconsumer.subscribe(
                listOf(
                        env.sm2013ManualHandlingTopic,
                        env.kafkaSm2013AutomaticDigitalHandlingTopic,
                        env.sm2013InvalidHandlingTopic
                )
        )
        while (applicationState.ready) {
            kafkaconsumer.poll(Duration.ofMillis(0)).filterNot { it.value() == null }.forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
                val loggingMeta = LoggingMeta(
                        mottakId = receivedSykmelding.navLogId,
                        orgNr = receivedSykmelding.legekontorOrgNr,
                        msgId = receivedSykmelding.msgId,
                        sykmeldingId = receivedSykmelding.sykmelding.id
                )
                handleMessageSykmelding(receivedSykmelding, database, loggingMeta, sykmeldingStatusKafkaProducer)
                if (it.topic() != env.sm2013InvalidHandlingTopic) {
                    sendtToMottattSykmeldingTopic(receivedSykmelding)
                }
            }
            delay(100)
        }
    }

    private fun sendtToMottattSykmeldingTopic(receivedSykmelding: ReceivedSykmelding) {
        val sykmelding = receivedSykmelding.toEnkelSykmelding()
        val message = MottattSykmeldingKafkaMessage(
                sykmelding = sykmelding,
                kafkaMetadata = KafkaMetadataDTO(
                        receivedSykmelding.msgId, receivedSykmelding.mottattDato.atOffset(ZoneOffset.UTC), receivedSykmelding.personNrPasient, "syfosmregister"
                )
        )
        mottattSykmeldingKafkaProducer.sendMottattSykmelding(message)
    }

    private suspend fun handleMessageSykmelding(
        receivedSykmelding: ReceivedSykmelding,
        database: DatabaseInterface,
        loggingMeta: LoggingMeta,
        sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottatt sykmelding SM2013, {}", StructuredArguments.fields(loggingMeta))

            if (database.connection.erSykmeldingsopplysningerLagret(receivedSykmelding.sykmelding.id)) {
                SYKMELDING_DUPLIKAT_COUNTER.inc()
                log.warn("Sykmelding med id {} allerede lagret i databasen, {}", receivedSykmelding.sykmelding.id, StructuredArguments.fields(loggingMeta))
            } else {
                database.lagreMottattSykmelding(
                        Sykmeldingsopplysninger(
                                id = receivedSykmelding.sykmelding.id,
                                pasientFnr = receivedSykmelding.personNrPasient,
                                pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                                legeFnr = receivedSykmelding.personNrLege,
                                legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
                                mottakId = receivedSykmelding.navLogId,
                                legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                                legekontorHerId = receivedSykmelding.legekontorHerId,
                                legekontorReshId = receivedSykmelding.legekontorReshId,
                                epjSystemNavn = receivedSykmelding.sykmelding.avsenderSystem.navn,
                                epjSystemVersjon = receivedSykmelding.sykmelding.avsenderSystem.versjon,
                                mottattTidspunkt = receivedSykmelding.mottattDato,
                                tssid = receivedSykmelding.tssid,
                                merknader = receivedSykmelding.merknader
                        ),
                        Sykmeldingsdokument(
                                id = receivedSykmelding.sykmelding.id,
                                sykmelding = receivedSykmelding.sykmelding
                        ))

                sykmeldingStatusKafkaProducer.send(SykmeldingStatusKafkaEventDTO(
                        receivedSykmelding.sykmelding.id,
                        receivedSykmelding.mottattDato.atOffset(ZoneOffset.UTC),
                        STATUS_APEN),
                        receivedSykmelding.personNrPasient)

                log.info("Sykmelding SM2013 lagret i databasen, {}", StructuredArguments.fields(loggingMeta))
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }
}
