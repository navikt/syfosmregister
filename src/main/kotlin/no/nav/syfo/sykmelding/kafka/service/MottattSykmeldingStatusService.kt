package no.nav.syfo.sykmelding.kafka.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.getArbeidsgiverStatus
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.kafka.service.KafkaModelMapper.Companion.toSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import java.time.OffsetDateTime
import java.time.ZoneOffset

class MottattSykmeldingStatusService(
    private val sykmeldingStatusService: SykmeldingStatusService,
    private val sendtSykmeldingKafkaProducer: SendtSykmeldingKafkaProducer,
    private val bekreftetSykmeldingKafkaProducer: BekreftSykmeldingKafkaProducer,
    private val tombstoneProducer: SykmeldingTombstoneProducer,
    private val databaseInterface: DatabaseInterface,
) {
    suspend fun handleStatusEventForResentSykmelding(sykmeldingId: String, fnr: String) {
        val status = sykmeldingStatusService.getLatestSykmeldingStatus(sykmeldingId)

        requireNotNull(status) {
            "Could not find status for sykmeldingId $sykmeldingId"
        }

        val sykmeldingStatusKafkaEventDTO = toSykmeldingStatusKafkaEventDTO(
            status,
            getArbeidsgiverStatus(sykmeldingId, status.event),
            getSporsmalOgSvar(sykmeldingId)
        )
        val kafkaMetadata = KafkaMetadataDTO(sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC), fnr, "syfosmregister")
        val sykmeldingStatus =
            SykmeldingStatusKafkaMessageDTO(kafkaMetadata = kafkaMetadata, event = sykmeldingStatusKafkaEventDTO)
        when (status.event) {
            StatusEvent.SENDT -> {
                log.info("Status is sendt, need to resendt to sendt-sykmelding-topic")
                sendtSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(sykmeldingStatus))
            }

            StatusEvent.BEKREFTET -> {
                log.info("Status is bekreftet, need to resendt to bekreftet-sykmelding-topic")
                bekreftetSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(sykmeldingStatus))
            }

            else -> {
                log.info("Does not need to resend sykmelding")
            }
        }
    }

    suspend fun handleStatusEvent(
        sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO,
        source: String = "on-prem",
    ) {
        log.info(
            "Got status update from $source kafka topic, sykmeldingId: {}, status: {}",
            sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId,
            sykmeldingStatusKafkaMessage.event.statusEvent
        )
        try {
            when (sykmeldingStatusKafkaMessage.event.statusEvent) {
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
                    slettSykmelding(sykmeldingStatusKafkaMessage)
                }

                else -> registrerStatus(sykmeldingStatusKafkaMessage)
            }
        } catch (e: Exception) {
            log.error(
                "Kunne ikke prosessere statusendring {} for sykmeldingid {} fordi {}",
                sykmeldingStatusKafkaMessage.event.statusEvent,
                sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId,
                e.message
            )
            throw e
        }
    }

    private suspend fun slettSykmelding(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val latestStatus =
            sykmeldingStatusService.getLatestSykmeldingStatus(sykmeldingStatusKafkaMessage.event.sykmeldingId)

        if (latestStatus == null) {
            log.warn("Sykmelding med id ${sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId} er allerede slettet")
        }

        when (latestStatus?.event) {
            StatusEvent.SENDT -> sendtSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
            StatusEvent.BEKREFTET -> bekreftetSykmeldingKafkaProducer.tombstoneSykmelding(
                sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId
            )

            else -> {}
        }
        tombstoneProducer.tombstoneSykmelding(sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
        sykmeldingStatusService.slettSykmelding(sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId)
    }

    private suspend fun handleSendtSykmelding(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val latestStatus =
            sykmeldingStatusService.getLatestSykmeldingStatus(sykmeldingStatusKafkaMessage.event.sykmeldingId)

        if (latestStatus?.event == StatusEvent.SENDT) {
            log.warn(
                "Sykmelding er allerede sendt sykmeldingId {}",
                sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId
            )
            return
        }

        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        publishToSendtSykmeldingTopic(sykmeldingStatusKafkaMessage)
        registrerSendt(sykmeldingStatusKafkaMessage)
    }

    private fun erAvvist(event: SykmeldingStatusKafkaEventDTO): Boolean =
        event.statusEvent == STATUS_BEKREFTET && event.sporsmals == null

    private suspend fun publishToBekreftSykmeldingTopic(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        bekreftetSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private suspend fun publishToSendtSykmeldingTopic(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sendtSykmeldingKafkaMessage = getKafkaMessage(sykmeldingStatusKafkaMessage)
        sendtSykmeldingKafkaProducer.sendSykmelding(sendtSykmeldingKafkaMessage)
    }

    private suspend fun getKafkaMessage(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO): SykmeldingKafkaMessage {
        val arbeidsgiverSykmelding =
            sykmeldingStatusService.getArbeidsgiverSykmelding(sykmeldingStatusKafkaMessage.event.sykmeldingId)
        val sendEvent = sykmeldingStatusKafkaMessage.event
        val metadata = sykmeldingStatusKafkaMessage.kafkaMetadata

        return SykmeldingKafkaMessage(arbeidsgiverSykmelding!!, metadata, sendEvent)
    }

    private suspend fun registrerBekreftet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        val sykmeldingBekreftEvent = SykmeldingBekreftEvent(
            sykmeldingStatusKafkaMessage.event.sykmeldingId,
            sykmeldingStatusKafkaMessage.event.timestamp,
            sykmeldingStatusKafkaMessage.event.sporsmals?.map {
                KafkaModelMapper.toSporsmal(
                    it,
                    sykmeldingStatusKafkaMessage.event.sykmeldingId
                )
            }
        )

        sykmeldingStatusService.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    private suspend fun registrerStatus(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage)
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)
        sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent)
    }

    private suspend fun sjekkStatusOgTombstone(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val lastStatus = sykmeldingStatusService.getLatestSykmeldingStatus(
            sykmeldingStatusKafkaMessage.kafkaMetadata.sykmeldingId,
        )

        if (lastStatus?.event == StatusEvent.BEKREFTET) {
            bekreftetSykmeldingKafkaProducer.tombstoneSykmelding(sykmeldingStatusKafkaMessage.event.sykmeldingId)
        }
    }

    private suspend fun registrerSendt(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessageDTO) {
        val arbeidsgiver: ArbeidsgiverStatusDTO = sykmeldingStatusKafkaMessage.event.arbeidsgiver
            ?: throw IllegalArgumentException("Arbeidsgiver er ikke oppgitt")
        if (sykmeldingStatusKafkaMessage.event.sporsmals?.first { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON } == null) {
            throw IllegalArgumentException("Mangler relevante spørsmål")
        }
        val sykmeldingId = sykmeldingStatusKafkaMessage.event.sykmeldingId
        val timestamp = sykmeldingStatusKafkaMessage.event.timestamp
        val sykmeldingSendEvent = SykmeldingSendEvent(
            sykmeldingId,
            timestamp,
            KafkaModelMapper.toArbeidsgiverStatus(sykmeldingId, arbeidsgiver),
            sykmeldingStatusKafkaMessage.event.sporsmals!!.map { KafkaModelMapper.toSporsmal(it, sykmeldingId) }
        )
        val sykmeldingStatusEvent = KafkaModelMapper.toSykmeldingStatusEvent(sykmeldingStatusKafkaMessage.event)

        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    private suspend fun getSporsmalOgSvar(sykmeldingId: String): List<Sporsmal> {
        return databaseInterface.hentSporsmalOgSvar(sykmeldingId)
    }

    private suspend fun getArbeidsgiverStatus(sykmeldingId: String, event: StatusEvent): ArbeidsgiverDbModel? {
        return when (event) {
            StatusEvent.SENDT -> databaseInterface.getArbeidsgiverStatus(sykmeldingId)
            else -> null
        }
    }
}
