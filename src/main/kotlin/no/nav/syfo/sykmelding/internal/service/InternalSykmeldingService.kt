package no.nav.syfo.sykmelding.internal.service

import no.nav.syfo.aksessering.db.hentSporsmalOgSvar
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.internal.db.getInternalSykmelding
import no.nav.syfo.sykmelding.internal.model.InternalSykmeldingDTO
import no.nav.syfo.sykmelding.internal.model.toInternalSykmelding

class InternalSykmeldingService(private val database: DatabaseInterface) {
    fun hentInternalSykmelding(fnr: String): List<InternalSykmeldingDTO> =
            database.getInternalSykmelding(fnr).map {
                val sporsmal = when {
                    it.status.statusEvent == "SENDT" || it.status.statusEvent == "BEKREFTET" -> database.connection.hentSporsmalOgSvar(it.id)
                    else -> emptyList()
                }
                it.toInternalSykmelding(sporsmal)
            }
}
