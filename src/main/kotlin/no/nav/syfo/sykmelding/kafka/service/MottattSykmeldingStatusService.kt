package no.nav.syfo.sykmelding.kafka.service

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.securelog
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.getArbeidsgiverStatus
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper.Companion.toSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService

class MottattSykmeldingStatusService(
    private val sykmeldingStatusService: SykmeldingStatusService,
    private val sendtSykmeldingKafkaProducer: SendtSykmeldingKafkaProducer,
    private val bekreftetSykmeldingKafkaProducer: BekreftSykmeldingKafkaProducer,
    private val tombstoneProducer: SykmeldingTombstoneProducer,
    private val databaseInterface: DatabaseInterface,
) {

    @WithSpan
    suspend fun handleStatusEventForResentSykmelding(
        @SpanAttribute sykmeldingId: String,
        fnr: String
    ) {
        val sykmeldingStatusKafkaEventDTO =
            withContext(Dispatchers.IO) {
                val status = sykmeldingStatusService.getLatestSykmeldingStatus(sykmeldingId)
                val tidligereArbeidsgiver =
                    sykmeldingStatusService.getTidligereArbeidsgiver(sykmeldingId)
                val alleSpm = sykmeldingStatusService.getAlleSpm(sykmeldingId)
                requireNotNull(status) { "Could not find status for sykmeldingId $sykmeldingId" }
                toSykmeldingStatusKafkaEventDTO(
                    status,
                    getArbeidsgiverStatus(sykmeldingId, status.event),
                    getSporsmalOgSvar(sykmeldingId),
                    tidligereArbeidsgiver,
                    alleSpm,
                )
            }

        val kafkaMetadata =
            KafkaMetadataDTO(
                sykmeldingId,
                OffsetDateTime.now(ZoneOffset.UTC),
                fnr,
                "syfosmregister",
            )
        val sykmeldingStatus =
            SykmeldingStatusKafkaMessageDTO(
                kafkaMetadata = kafkaMetadata,
                event = sykmeldingStatusKafkaEventDTO,
            )
        when (sykmeldingStatusKafkaEventDTO.statusEvent) {
            STATUS_SENDT -> {
                log.info("Status is sendt, need to resendt to sendt-sykmelding-topic")
                sendtSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(sykmeldingStatus))
            }
            STATUS_BEKREFTET -> {
                log.info("Status is bekreftet, need to resendt to bekreftet-sykmelding-topic")
                securelog.info("sender med tidligere arbeidsgiver $sykmeldingStatusKafkaEventDTO")
                bekreftetSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(sykmeldingStatus))
            }
            else -> {
                log.info(
                    "Does not need to resend sykmelding ${sykmeldingStatusKafkaEventDTO.statusEvent}"
                )
            }
        }
    }

    suspend fun handleStatusEvent(
        sykmeldingId: String,
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO?,
    ) {
        log.info(
            "Got status update kafka topic, sykmeldingId: $sykmeldingId, status: ${sykmeldingStatusKafkaMessage?.event?.statusEvent}",
        )
        try {
            val span = Span.current()
            span.setAttribute(
                "sykmeldingId",
                sykmeldingId,
            )

            when (sykmeldingStatusKafkaMessage?.event?.statusEvent) {
                STATUS_SENDT -> {
                    handleSendtSykmelding(sykmeldingStatusKafkaMessage)
                }
                STATUS_BEKREFTET -> {
                    if (!erAvvist(sykmeldingStatusKafkaMessage.event)) {
                        publishToBekreftSykmeldingTopic(sykmeldingStatusKafkaMessage)
                    }
                    registrerBekreftet(sykmeldingStatusKafkaMessage)
                }
                STATUS_SLETTET -> {
                    slettSykmelding(sykmeldingId)
                }
                null -> {
                    slettSykmelding(sykmeldingId)
                }
                else -> registrerStatus(sykmeldingStatusKafkaMessage)
            }
        } catch (e: Exception) {
            log.error(
                "Kunne ikke prosessere statusendring ${sykmeldingStatusKafkaMessage?.event?.statusEvent}} for sykmeldingid $sykmeldingId",
                e.message,
            )
            throw e
        }
    }

    private suspend fun slettSykmelding(sykmeldingId: String) {
        val latestStatus = sykmeldingStatusService.getLatestSykmeldingStatus(sykmeldingId)

        if (latestStatus == null) {
            log.warn(
                "Sykmelding med id $sykmeldingId er allerede slettet",
            )
        }

        tombstoneProducer.tombstoneSykmelding(
            sykmeldingId,
        )
        sykmeldingStatusService.slettSykmelding(
            sykmeldingId,
        )
    }

    private suspend fun handleSendtSykmelding(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val latestStatus =
            sykmeldingStatusService.getLatestSykmeldingStatus(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
            )

        if (
            latestStatus?.event == StatusEvent.SENDT &&
                // Når sykmeldinger-backend sender oppdaterte sykmelding så skal
                // vi sende den på nytt på syfo-sendt-sykmelding
                sykmeldingStatusKafkaMessage.event.erSvarOppdatering != true
        ) {
            log.warn(
                "Sykmelding er allerede sendt sykmeldingId {}",
                sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId,
            )
            return
        }

        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        publishToSendtSykmeldingTopic(sykmeldingStatusKafkaMessage)
        registrerSendt(sykmeldingStatusKafkaMessage)
    }

    private fun erAvvist(event: SykmeldingStatusKafkaEventDTO): Boolean =
        event.statusEvent == STATUS_BEKREFTET && event.sporsmals == null

    private suspend fun publishToBekreftSykmeldingTopic(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        bekreftetSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private suspend fun publishToSendtSykmeldingTopic(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        sendtSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private suspend fun getKafkaMessage(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ): SykmeldingKafkaMessage {
        val arbeidsgiverSykmelding =
            sykmeldingStatusService.getArbeidsgiverSykmelding(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
            )
                ?: throw RuntimeException(
                    "Could not find sykmelding ${sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId}",
                )
        val sendEvent = sykmeldingStatusKafkaMessage.event
        val metadata = sykmeldingStatusKafkaMessage.kafkaMetadata

        return SykmeldingKafkaMessage(arbeidsgiverSykmelding, metadata, sendEvent)
    }

    private suspend fun registrerBekreftet(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val sykmeldingStatusEvent =
            KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        val tidligereArbeidsgiver = sykmeldingStatusKafkaMessage.event.tidligereArbeidsgiver

        val sykmeldingBekreftEvent =
            SykmeldingBekreftEvent(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
                sykmeldingStatusKafkaMessage.event.timestamp,
                sykmeldingStatusKafkaMessage.event.sporsmals?.map {
                    KafkaModelMapper.toSporsmal(
                        it,
                        sykmeldingStatusKafkaMessage.event.sykmeldingId,
                    )
                },
                brukerSvar = sykmeldingStatusKafkaMessage.event.brukerSvar,
            )

        sykmeldingStatusService.registrerBekreftet(
            sykmeldingBekreftEvent,
            sykmeldingStatusEvent,
            tidligereArbeidsgiver,
        )
    }

    private suspend fun registrerStatus(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        val sykmeldingStatusEvent =
            KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent)
    }

    private suspend fun sjekkStatusOgTombstone(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val lastStatus =
            sykmeldingStatusService.getLatestSykmeldingStatus(
                sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId,
            )

        if (
            lastStatus?.event == StatusEvent.BEKREFTET &&
                lastStatus.timestamp.isBefore(sykmeldingStatusKafkaMessage.event.timestamp)
        ) {
            bekreftetSykmeldingKafkaProducer.tombstoneSykmelding(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
            )
        }
    }

    private suspend fun registrerSendt(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO
    ) {
        val arbeidsgiver: ArbeidsgiverStatusKafkaDTO =
            sykmeldingStatusKafkaMessage.event.arbeidsgiver
                ?: throw IllegalArgumentException("Arbeidsgiver er ikke oppgitt")
        if (
            sykmeldingStatusKafkaMessage.event.sporsmals?.first { sporsmal ->
                sporsmal.shortName == ShortNameKafkaDTO.ARBEIDSSITUASJON
            } == null
        ) {
            throw IllegalArgumentException("Mangler relevante spørsmål")
        }
        val sykmeldingId = sykmeldingStatusKafkaMessage.event.sykmeldingId
        val timestamp = sykmeldingStatusKafkaMessage.event.timestamp
        val sykmeldingSendEvent =
            SykmeldingSendEvent(
                sykmeldingId,
                timestamp,
                KafkaModelMapper.toArbeidsgiverStatus(sykmeldingId, arbeidsgiver),
                sykmeldingStatusKafkaMessage.event.sporsmals.map {
                    KafkaModelMapper.toSporsmal(it, sykmeldingId)
                },
                brukerSvar = sykmeldingStatusKafkaMessage.event.brukerSvar,
            )
        val sykmeldingStatusEvent =
            KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)

        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    private suspend fun getSporsmalOgSvar(sykmeldingId: String): List<Sporsmal> {
        return databaseInterface.hentSporsmalOgSvar(sykmeldingId)
    }

    private fun getArbeidsgiverStatus(
        sykmeldingId: String,
        event: StatusEvent
    ): ArbeidsgiverDbModel? {
        return when (event) {
            StatusEvent.SENDT -> databaseInterface.getArbeidsgiverStatus(sykmeldingId)
            else -> null
        }
    }
}
