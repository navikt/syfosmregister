package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.db.DatabaseInterface

class SykmeldingStatusService(private val database: DatabaseInterface) {

    fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
    }

    fun registrerSendt(sykmeldingSendEvent: SykmeldingSendEvent) {
        val sykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingSendEvent.sykmeldingId, sykmeldingSendEvent.timestamp, StatusEvent.SENDT)
        database.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
    }

    fun registrerBekreftet(sykmeldingBekreftEvent: SykmeldingBekreftEvent) {
        val sykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingBekreftEvent.sykmeldingId, sykmeldingBekreftEvent.timestamp, StatusEvent.BEKREFTET)
        database.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
    }

    fun getSykmeldingStatus(sykmeldingsid: String, filter: String?): List<SykmeldingStatusEvent> {
        val sykmeldingStatus = database.hentSykmeldingStatuser(sykmeldingsid)
        return when (filter) {
            "LATEST" -> getLatestSykmeldingStatus(sykmeldingStatus)
            else -> sykmeldingStatus
        }
    }

    private fun getLatestSykmeldingStatus(sykmeldingStatus: List<SykmeldingStatusEvent>): List<SykmeldingStatusEvent> {
        val latest = sykmeldingStatus.maxBy { it.timestamp }
        return when (latest) {
            null -> emptyList()
            else -> listOf(latest)
        }
    }

    fun erEier(sykmeldingsid: String, fnr: String): Boolean = database.erEier(sykmeldingsid, fnr)
}
