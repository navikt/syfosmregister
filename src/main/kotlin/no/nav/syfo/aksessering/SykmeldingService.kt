package no.nav.syfo.aksessering

import no.nav.syfo.aksessering.api.SykmeldingDTO
import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.toFullstendigDTO
import no.nav.syfo.domain.toInternalDTO
import no.nav.syfo.domain.toSkjermetDTO

class SykmeldingService(private val database: DatabaseInterface) {
    fun hentSykmeldinger(fnr: String): List<SykmeldingDTO> =
            database.hentSykmeldinger(fnr).map {
                when (it.skjermesForPasient) {
                    true -> it.toSkjermetDTO()
                    false -> it.toFullstendigDTO()
                }
            }

    fun erEier(sykmeldingsid: String, fnr: String): Boolean = database.erEier(sykmeldingsid, fnr)

    fun hentInternalSykmelding(fnr: String) =
        database.hentSykmeldinger(fnr).map { it.toInternalDTO() }
}
