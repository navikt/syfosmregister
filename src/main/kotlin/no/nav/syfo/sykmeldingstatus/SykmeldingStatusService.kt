package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.aksessering.db.registerStatus
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent

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
