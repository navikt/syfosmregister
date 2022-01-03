package no.nav.syfo.identendring

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import no.nav.syfo.metrics.NYTT_FNR_COUNTER
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.db.Periode
import no.nav.syfo.sykmelding.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfallForFnr
import no.nav.syfo.sykmelding.db.updateFnr
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import java.time.LocalDate

class IdentendringService(
    private val database: DatabaseInterface,
    private val sendtSykmeldingKafkaProducer: SendtSykmeldingKafkaProducer
) {
    fun oppdaterIdent(identListe: List<Ident>): Int {
        if (harEndretFnr(identListe)) {
            val nyttFnr = identListe.find { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }?.idnummer
                ?: throw IllegalStateException("Mangler gyldig fnr!")
            val tidligereFnr = identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT && !it.gjeldende }

            val sykmeldinger = tidligereFnr.flatMap { database.getSykmeldingerMedIdUtenBehandlingsutfallForFnr(it.idnummer) }

            if (sykmeldinger.isNotEmpty()) {
                val sendteSykmeldingerSisteFireMnd = sykmeldinger.filter {
                    it.status.statusEvent == STATUS_SENDT && finnSisteTom(it.sykmeldingsDokument.perioder).isAfter(
                        LocalDate.now().minusMonths(4)
                    )
                }
                log.info("Resender ${sendteSykmeldingerSisteFireMnd.size} sendte sykmeldinger")
                sendteSykmeldingerSisteFireMnd.forEach {
                    sendtSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(it, nyttFnr))
                }

                var oppdaterteSykmeldinger = 0
                tidligereFnr.forEach { oppdaterteSykmeldinger += database.updateFnr(nyttFnr = nyttFnr, fnr = it.idnummer) }
                log.info("Har oppdatert $oppdaterteSykmeldinger sykmeldinger i databasen")
                NYTT_FNR_COUNTER.inc()
                return oppdaterteSykmeldinger
            }
        }
        return 0
    }

    private fun harEndretFnr(identListe: List<Ident>): Boolean {
        if (identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT }.size < 2) {
            log.debug("Identendring inneholder ingen endring i fnr")
            return false
        }
        return true
    }

    private fun getKafkaMessage(sykmelding: SykmeldingDbModelUtenBehandlingsutfall, nyttFnr: String): SykmeldingKafkaMessage {
        val sendtSykmelding = sykmelding.toArbeidsgiverSykmelding()
        val metadata = KafkaMetadataDTO(
            sykmeldingId = sykmelding.id,
            timestamp = sykmelding.status.statusTimestamp,
            source = "pdl",
            fnr = nyttFnr
        )
        val sendEvent = SykmeldingStatusKafkaEventDTO(
            metadata.sykmeldingId,
            metadata.timestamp,
            STATUS_SENDT,
            ArbeidsgiverStatusDTO(
                sykmelding.status.arbeidsgiver!!.orgnummer,
                sykmelding.status.arbeidsgiver.juridiskOrgnummer,
                sykmelding.status.arbeidsgiver.orgNavn
            ),
            listOf(
                SporsmalOgSvarDTO(
                    tekst = "Jeg er sykmeldt fra",
                    shortName = ShortNameDTO.ARBEIDSSITUASJON,
                    svartype = SvartypeDTO.ARBEIDSSITUASJON,
                    svar = "ARBEIDSTAKER"
                )
            )
        )
        return SykmeldingKafkaMessage(sendtSykmelding, metadata, sendEvent)
    }

    private fun finnSisteTom(perioder: List<Periode>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
