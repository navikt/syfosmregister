package no.nav.syfo.sykmeldingstatus.kafka.service

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingBekreftEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingSendEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.tilArbeidsgiver
import no.nav.syfo.sykmeldingstatus.api.tilSporsmal
import no.nav.syfo.sykmeldingstatus.api.tilSporsmalListe
import no.nav.syfo.sykmeldingstatus.api.toStatusEvent
import no.nav.syfo.sykmeldingstatus.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessage
import org.slf4j.LoggerFactory

class SykmeldingStatusConsumerService(
    val sykmeldingStatusService: SykmeldingStatusService,
    val kafkaConsumer: SykmeldingStatusKafkaConsumer,
    val applicationState: ApplicationState,
    val ignoredSources: List<String>
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingStatusKafkaConsumer::class.java)
    }

    suspend fun start() {
        kafkaConsumer.subscribe()
        while (applicationState.alive) {
            val kafkaEvents = kafkaConsumer.poll()
            kafkaEvents.asSequence()
                    .filter(ignoreSources())
                    .forEach(handleStatusEvent())
        }
    }

    private fun handleStatusEvent(): (SykmeldingStatusKafkaMessage) -> Unit = { sykmeldingStatusKafkaMessage ->
        when (sykmeldingStatusKafkaMessage.event.statusEvent) {
            StatusEventDTO.SENDT -> registrerSendt(sykmeldingStatusKafkaMessage)
            StatusEventDTO.BEKREFTET -> registrerBekreftet(sykmeldingStatusKafkaMessage)
            else -> registrerStatus(sykmeldingStatusKafkaMessage)
        }
    }

    private fun registrerStatus(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessage) {
        sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
                sykmeldingStatusKafkaMessage.event.timestamp,
                sykmeldingStatusKafkaMessage.event.statusEvent.toStatusEvent()
        ))
    }

    private fun registrerBekreftet(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessage) {
        sykmeldingStatusService.registrerBekreftet(SykmeldingBekreftEvent(
                sykmeldingStatusKafkaMessage.event.sykmeldingId,
                sykmeldingStatusKafkaMessage.event.timestamp,
                tilSporsmalListe(sykmeldingStatusKafkaMessage.event.sykmeldingId, sykmeldingStatusKafkaMessage.event.sporsmals)
        ))
    }

    private fun registrerSendt(sykmeldingStatusKafkaMessage: SykmeldingStatusKafkaMessage) {
        val arbeidsgiver: ArbeidsgiverDTO = sykmeldingStatusKafkaMessage.event.arbeidsgiver
                ?: throw IllegalArgumentException("Arbeidsgiver er ikke oppgitt")
        val arbeidsgiverSporsmal: SporsmalOgSvarDTO = sykmeldingStatusKafkaMessage.event.sporsmals?.first { sporsmal -> sporsmal.shortName == ShortNameDTO.ARBEIDSSITUASJON }
                ?: throw IllegalArgumentException("Ingen sporsmal funnet")
        val sykmeldingId = sykmeldingStatusKafkaMessage.event.sykmeldingId
        val timestamp = sykmeldingStatusKafkaMessage.event.timestamp
        val sykmeldingSendEvent = SykmeldingSendEvent(sykmeldingId, timestamp,
                tilArbeidsgiver(sykmeldingId, arbeidsgiver),
                tilSporsmal(sykmeldingId, arbeidsgiverSporsmal))
        sykmeldingStatusService.registrerSendt(sykmeldingSendEvent)
    }

    private fun ignoreSources(): (SykmeldingStatusKafkaMessage) -> Boolean = { sykmeldingStatusKafkaMessage ->
        !ignoredSources.contains(sykmeldingStatusKafkaMessage.kafkaMetadata.source)
    }
}
