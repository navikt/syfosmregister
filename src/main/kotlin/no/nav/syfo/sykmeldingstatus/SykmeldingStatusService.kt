package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.db.DatabaseInterface

class SykmeldingStatusService(private val database: DatabaseInterface) {

    fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
    }

    fun registrerSendt(sykmeldingSendEvent: SykmeldingSendEvent) {
        val sykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingSendEvent.id, sykmeldingSendEvent.timestamp, StatusEvent.SENDT)
        registrerStatus(sykmeldingStatusEvent)
        database.lagreArbeidsgiver(sykmeldingSendEvent)
        database.lagreSporsmalOgSvar(sykmeldingSendEvent.sporsmal)
    }
}
