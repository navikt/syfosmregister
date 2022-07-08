package no.nav.syfo.identendring

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.log
import no.nav.syfo.metrics.NYTT_FNR_COUNTER
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.getSykmeldingerMedIdUtenBehandlingsutfallForFnr
import no.nav.syfo.sykmelding.db.updateFnr

class UpdateIdentService(
    private val database: DatabaseInterface,
    private val pdlService: PdlPersonService
) {
    suspend fun oppdaterIdent(identListe: List<Ident>): Int {
        if (harEndretFnr(identListe)) {
            val nyttFnr = identListe.find { it.type == IdentType.FOLKEREGISTERIDENT && it.gjeldende }?.idnummer
                ?: throw IllegalStateException("Mangler gyldig fnr!")

            val tidligereFnr = identListe.filter { it.type == IdentType.FOLKEREGISTERIDENT && !it.gjeldende }
            val sykmeldinger = tidligereFnr.flatMap { database.getSykmeldingerMedIdUtenBehandlingsutfallForFnr(it.idnummer) }

            if (sykmeldinger.isNotEmpty()) {
                sjekkPDL(nyttFnr)
                var oppdaterteSykmeldinger = 0
                tidligereFnr.forEach { oppdaterteSykmeldinger += database.updateFnr(nyttFnr = nyttFnr, fnr = it.idnummer) }
                log.info("Har oppdatert $oppdaterteSykmeldinger sykmeldinger i databasen")
                NYTT_FNR_COUNTER.inc()
                return oppdaterteSykmeldinger
            }
        }
        return 0
    }

    private suspend fun sjekkPDL(nyttFnr: String) {
        val pdlPerson = pdlService.getPdlPerson(nyttFnr)
        if (pdlPerson.fnr != nyttFnr || pdlPerson.identer.any { it.ident == nyttFnr && it.historisk }) {
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
}
