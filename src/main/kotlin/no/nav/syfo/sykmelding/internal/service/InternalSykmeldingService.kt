package no.nav.syfo.sykmelding.internal.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.internal.db.getInternalSykmelding
import no.nav.syfo.sykmelding.internal.model.InternalSykmeldingDTO
import no.nav.syfo.sykmelding.internal.model.toInternalSykmelding

class InternalSykmeldingService(private val database: DatabaseInterface) {
    fun hentInternalSykmelding(fnr: String): List<InternalSykmeldingDTO> =
        database.getInternalSykmelding(fnr).map {
                it.toInternalSykmelding()
        }
}
