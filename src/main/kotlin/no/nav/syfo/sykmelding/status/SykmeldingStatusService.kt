package no.nav.syfo.sykmelding.status

import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfall
import no.nav.syfo.sykmelding.kafka.model.EnkelSykmelding
import no.nav.syfo.sykmelding.kafka.model.toEnkelSykmelding

class SykmeldingStatusService(private val database: DatabaseInterface) {

    fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
    }

    fun registrerSendt(
        sykmeldingSendEvent: SykmeldingSendEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingSendEvent.sykmeldingId, sykmeldingSendEvent.timestamp, StatusEvent.SENDT)
    ) {
        database.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    fun registrerBekreftet(
        sykmeldingBekreftEvent: SykmeldingBekreftEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingBekreftEvent.sykmeldingId, sykmeldingBekreftEvent.timestamp, StatusEvent.BEKREFTET)
    ) {
        database.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    fun getSykmeldingStatus(sykmeldingsid: String, filter: String?): List<SykmeldingStatusEvent> {
        val sykmeldingStatus = database.hentSykmeldingStatuser(sykmeldingsid)
        return when (filter) {
            "LATEST" -> getLatestSykmeldingStatus(sykmeldingStatus)
            else -> sykmeldingStatus
        }
    }

    fun getEnkelSykmelding(sykmeldingId: String): EnkelSykmelding? =
        database.getSykmeldingerMedIdUtenBehandlingsutfall(sykmeldingId)?.toEnkelSykmelding()

    private fun getLatestSykmeldingStatus(sykmeldingStatus: List<SykmeldingStatusEvent>): List<SykmeldingStatusEvent> {
        val latest = sykmeldingStatus.maxBy { it.timestamp }
        return when (latest) {
            null -> emptyList()
            else -> listOf(latest)
        }
    }

    fun erEier(sykmeldingsid: String, fnr: String): Boolean = database.erEier(sykmeldingsid, fnr)
}
