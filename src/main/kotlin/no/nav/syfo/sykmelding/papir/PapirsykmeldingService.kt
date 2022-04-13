package no.nav.syfo.sykmelding.papir

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.papir.db.getPapirsykmelding
import no.nav.syfo.sykmelding.papir.db.toPapirsykmeldingDTO
import no.nav.syfo.sykmelding.papir.model.PapirsykmeldingDTO

class PapirsykmeldingService(private val database: DatabaseInterface) {
    fun getPapirsykmelding(sykmeldingId: String): PapirsykmeldingDTO? {
        return database.getPapirsykmelding(sykmeldingId)?.toPapirsykmeldingDTO()
    }
}
