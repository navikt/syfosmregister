package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmeldingstatus.kafka.model.toSykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusBackupKafkaProducer

class SykmeldingStatusService(private val database: DatabaseInterface, private val sykmeldingStatusKafkaProducer: SykmeldingStatusBackupKafkaProducer) {

    fun registrerStatus(sykmeldingStatusEvent: SykmeldingStatusEvent) {
        database.registerStatus(sykmeldingStatusEvent)
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusEvent.toSykmeldingStatusKafkaEvent(sykmeldingStatusEvent.sykmeldingId))
    }

    fun registrerSendt(
        sykmeldingSendEvent: SykmeldingSendEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingSendEvent.sykmeldingId, sykmeldingSendEvent.timestamp, StatusEvent.SENDT)
    ) {
        database.registrerSendt(sykmeldingSendEvent, sykmeldingStatusEvent)
        sykmeldingStatusKafkaProducer.send(sykmeldingSendEvent.toSykmeldingStatusKafkaEvent(sykmeldingSendEvent.sykmeldingId))
    }

    fun registrerBekreftet(
        sykmeldingBekreftEvent: SykmeldingBekreftEvent,
        sykmeldingStatusEvent: SykmeldingStatusEvent = SykmeldingStatusEvent(sykmeldingBekreftEvent.sykmeldingId, sykmeldingBekreftEvent.timestamp, StatusEvent.BEKREFTET)
    ) {
        database.registrerBekreftet(sykmeldingBekreftEvent, sykmeldingStatusEvent)
        sykmeldingStatusKafkaProducer.send(sykmeldingBekreftEvent.toSykmeldingStatusKafkaEvent(sykmeldingBekreftEvent.sykmeldingId))
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
