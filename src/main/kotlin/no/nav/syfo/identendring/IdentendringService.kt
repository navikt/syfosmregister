package no.nav.syfo.identendring

import java.time.LocalDate
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import no.nav.syfo.metrics.NYTT_FNR_COUNTER
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.Periode
import no.nav.syfo.sykmelding.db.SykmeldingDbModelUtenBehandlingsutfall
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfallForFnr
import no.nav.syfo.sykmelding.db.updateFnr
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.toArbeidsgiverSykmelding
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer

class IdentendringService(
    private val database: DatabaseInterface,
    private val sendtSykmeldingKafkaProducer: SendtSykmeldingKafkaProducer,
    private val pdlService: PdlPersonService,
) {
    suspend fun oppdaterIdent(identListe: List<Ident>): Int {
        if (harEndretFnr(identListe)) {
            val nyttFnr =
                identListe
                    .find { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }
                    ?.idnummer
                    ?: throw IllegalStateException("Mangler gyldig fnr!")

            val tidligereFnr =
                identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT && !it.gjeldende }
            val sykmeldinger =
                tidligereFnr.flatMap {
                    database.getSykmeldingerMedIdUtenBehandlingsutfallForFnr(it.idnummer)
                }

            if (sykmeldinger.isNotEmpty()) {
                sjekkPDL(nyttFnr)

                val sendteSykmeldingerSisteFireMnd =
                    sykmeldinger.filter {
                        it.status.statusEvent == STATUS_SENDT &&
                            finnSisteTom(it.sykmeldingsDokument.perioder)
                                .isAfter(
                                    LocalDate.now().minusMonths(4),
                                )
                    }
                log.info("Resender ${sendteSykmeldingerSisteFireMnd.size} sendte sykmeldinger")
                sendteSykmeldingerSisteFireMnd.forEach {
                    sendtSykmeldingKafkaProducer.sendSykmelding(getKafkaMessage(it, nyttFnr))
                }

                var oppdaterteSykmeldinger = 0
                tidligereFnr.forEach {
                    oppdaterteSykmeldinger +=
                        database.updateFnr(nyttFnr = nyttFnr, fnr = it.idnummer)
                }
                log.info("Har oppdatert $oppdaterteSykmeldinger sykmeldinger i databasen")
                NYTT_FNR_COUNTER.inc()
                return oppdaterteSykmeldinger
            }
        }
        return 0
    }

    private suspend fun sjekkPDL(nyttFnr: String) {
        val pdlPerson = pdlService.getPdlPerson(nyttFnr)
        if (
            pdlPerson.fnr != nyttFnr ||
                pdlPerson.identer.any { it.ident == nyttFnr && it.historisk }
        ) {
            throw InactiveIdentException("Nytt FNR er ikke aktivt FNR i PDL API")
        }
    }

    private fun harEndretFnr(identListe: List<Ident>): Boolean {
        if (identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT }.size < 2) {
            log.debug("Identendring inneholder ingen endring i fnr")
            return false
        }
        return true
    }

    private fun getKafkaMessage(
        sykmelding: SykmeldingDbModelUtenBehandlingsutfall,
        nyttFnr: String
    ): SykmeldingKafkaMessage {
        val sendtSykmelding = sykmelding.toArbeidsgiverSykmelding()
        val metadata =
            KafkaMetadataDTO(
                sykmeldingId = sykmelding.id,
                timestamp = sykmelding.status.statusTimestamp,
                source = "pdl",
                fnr = nyttFnr,
            )
        val sendEvent =
            SykmeldingStatusKafkaEventDTO(
                metadata.sykmeldingId,
                metadata.timestamp,
                STATUS_SENDT,
                ArbeidsgiverStatusKafkaDTO(
                    sykmelding.status.arbeidsgiver!!.orgnummer,
                    sykmelding.status.arbeidsgiver.juridiskOrgnummer,
                    sykmelding.status.arbeidsgiver.orgNavn,
                ),
                listOf(
                    SporsmalOgSvarKafkaDTO(
                        tekst = "Jeg er sykmeldt fra",
                        shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
                        svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
                        svar = "ARBEIDSTAKER",
                    ),
                ),
            )
        return SykmeldingKafkaMessage(sendtSykmelding, metadata, sendEvent)
    }

    private fun finnSisteTom(perioder: List<Periode>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
