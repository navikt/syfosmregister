package no.nav.syfo.sykmelding.status

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.nullstilling.slettSykmelding
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfall
import no.nav.syfo.sykmelding.kafka.model.KomplettInnsendtSkjemaSvar
import no.nav.syfo.sykmelding.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding

class SykmeldingStatusService(private val database: DatabaseInterface) {

    suspend fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
    }

    suspend fun registrerSendt(
        sykmeldingSendEvent: SykmeldingSendEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent =
            SykmeldingStatusEvent(
                sykmeldingSendEvent.sykmeldingId,
                sykmeldingSendEvent.timestamp,
                StatusEvent.SENDT
            ),
    ) {
        database.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    suspend fun registrerBekreftet(
        sykmeldingBekreftEvent: SykmeldingBekreftEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent =
            SykmeldingStatusEvent(
                sykmeldingBekreftEvent.sykmeldingId,
                sykmeldingBekreftEvent.timestamp,
                StatusEvent.BEKREFTET
            ),
        tidligereArbeidsgiver: TidligereArbeidsgiverKafkaDTO?
    ) {
        database.registrerBekreftet(
            sykmeldingBekreftEvent,
            sykmeldingStatusEvent,
            tidligereArbeidsgiver
        )
    }

    suspend fun getTidligereArbeidsgiver(sykmeldingId: String) =
        database.getTidligereArbeidsgiver(sykmeldingId).singleOrNull()?.tidligereArbeidsgiver

    @WithSpan
    suspend fun getLatestSykmeldingStatus(
        @SpanAttribute sykmeldingId: String
    ): SykmeldingStatusEvent? =
        database.hentSykmeldingStatuser(sykmeldingId).maxByOrNull { it.timestamp }

    suspend fun getArbeidsgiverSykmelding(sykmeldingId: String): ArbeidsgiverSykmelding? =
        database.getSykmeldingerMedIdUtenBehandlingsutfall(sykmeldingId)?.toArbeidsgiverSykmelding()

    suspend fun slettSykmelding(sykmeldingId: String) {
        database.slettSykmelding(sykmeldingId)
    }

    suspend fun getAlleSpm(sykmeldingId: String): KomplettInnsendtSkjemaSvar? {
        return database.getAlleSpm(sykmeldingId)
    }
}
