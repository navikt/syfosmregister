package no.nav.syfo.sykmeldingstatus

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
}
