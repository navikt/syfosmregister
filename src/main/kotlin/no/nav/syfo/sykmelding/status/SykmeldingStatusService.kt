package no.nav.syfo.sykmelding.status

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.nullstilling.slettSykmelding
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfall
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding

class SykmeldingStatusService(private val database: DatabaseInterface) {

    suspend fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
    }

    suspend fun registrerSendt(
        sykmeldingSendEvent: SykmeldingSendEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingSendEvent.sykmeldingId, sykmeldingSendEvent.timestamp, StatusEvent.SENDT)
    ) {
        database.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    suspend fun registrerBekreftet(
        sykmeldingBekreftEvent: SykmeldingBekreftEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingBekreftEvent.sykmeldingId, sykmeldingBekreftEvent.timestamp, StatusEvent.BEKREFTET)
    ) {
        database.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    suspend fun getSykmeldingStatus(sykmeldingsid: String, filter: String?): List<SykmeldingStatusEvent> {
        val sykmeldingStatus = database.hentSykmeldingStatuser(sykmeldingsid)
        return when (filter) {
            "LATEST" -> getLatestSykmeldingStatus(sykmeldingStatus)
            else -> sykmeldingStatus
        }
    }

    suspend fun getArbeidsgiverSykmelding(sykmeldingId: String): ArbeidsgiverSykmelding? =
        database.getSykmeldingerMedIdUtenBehandlingsutfall(sykmeldingId)?.toArbeidsgiverSykmelding()

    private fun getLatestSykmeldingStatus(sykmeldingStatus: List<SykmeldingStatusEvent>): List<SykmeldingStatusEvent> {
        val latest = sykmeldingStatus.maxByOrNull { it.timestamp }
        return when (latest) {
            null -> emptyList()
            else -> listOf(latest)
        }
    }

    suspend fun erEier(sykmeldingsid: String, fnr: String): Boolean = database.erEier(sykmeldingsid, fnr)

    suspend fun slettSykmelding(sykmeldingId: String) {
        database.slettSykmelding(sykmeldingId)
    }
}
