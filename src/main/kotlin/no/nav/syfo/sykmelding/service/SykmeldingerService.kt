package no.nav.syfo.sykmelding.service

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.db.getSykmeldingerMedId
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.toSykmeldingDTO
import no.nav.syfo.sykmeldingstatus.Sporsmal

class SykmeldingerService(private val database: DatabaseInterface) {
    fun getInternalSykmeldinger(fnr: String): List<SykmeldingDTO> =
            getSykmeldingerWithSporsmal(fnr)

    fun getUserSykmelding(fnr: String): List<SykmeldingDTO> =
            getSykmeldingerWithSporsmal(fnr, true)

    fun getSykmeldingMedId(sykmeldingId: String): SykmeldingDTO? =
        database.getSykmeldingerMedId(sykmeldingId)?.let {
            it.toSykmeldingDTO(sporsmal = getSporsmal(it), isPasient = false, ikkeTilgangTilDiagnose = true)
        }

    private fun getSykmeldingerWithSporsmal(fnr: String, isPasient: Boolean = false): List<SykmeldingDTO> {
        return database.getSykmeldinger(fnr).map {
            it.toSykmeldingDTO(sporsmal = getSporsmal(it), isPasient = isPasient, ikkeTilgangTilDiagnose = false)
        }
    }

    private fun getSporsmal(sykmelding: SykmeldingDbModel): List<Sporsmal> {
        val sporsmal = when {
            sykmelding.status.statusEvent == "SENDT" || sykmelding.status.statusEvent == "BEKREFTET" -> database.hentSporsmalOgSvar(sykmelding.id)
            else -> emptyList()
        }
        return sporsmal
    }
}
