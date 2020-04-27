package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfall
import no.nav.syfo.sykmeldingstatus.kafka.model.SendtSykmelding
import no.nav.syfo.sykmeldingstatus.kafka.model.toSendtSykmelding

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

    fun getSendtSykmeldingUtenDiagnose(sykmeldingId: String): SendtSykmelding? =
        database.getSykmeldingerMedIdUtenBehandlingsutfall(sykmeldingId)?.toSendtSykmelding()

    private fun getLatestSykmeldingStatus(sykmeldingStatus: List<SykmeldingStatusEvent>): List<SykmeldingStatusEvent> {
        val latest = sykmeldingStatus.maxBy { it.timestamp }
        return when (latest) {
            null -> emptyList()
            else -> listOf(latest)
        }
    }

    fun erEier(sykmeldingsid: String, fnr: String): Boolean = database.erEier(sykmeldingsid, fnr)
}
