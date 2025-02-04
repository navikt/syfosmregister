package no.nav.syfo.sykmelding.service

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.getSykInnSykmeldingForIdent
import no.nav.syfo.sykmelding.db.getSykInnSykmeldingerMedId
import no.nav.syfo.sykmelding.db.getSykmelding
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.db.getSykmeldingerMedId
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.sykinn.SykInnSykmeldingDTO
import no.nav.syfo.sykmelding.model.toSykmeldingDTO
import no.nav.syfo.sykmelding.serviceuser.api.model.SykmeldtStatus
import no.nav.syfo.sykmelding.status.Sporsmal

class SykmeldingerService(private val database: DatabaseInterface) {
    suspend fun getInternalSykmeldinger(
        fnr: String,
        fom: LocalDate? = null,
        tom: LocalDate? = null
    ): List<SykmeldingDTO> =
        getSykmeldingerWithSporsmal(fnr).filter(filterFomDate(fom)).filter(filterTomDate(tom))

    suspend fun getUserSykmelding(
        fnr: String,
        fom: LocalDate?,
        tom: LocalDate?,
        include: List<String>? = null,
        exclude: List<String>? = null,
        fullBehandler: Boolean = true
    ): List<SykmeldingDTO> {
        return getSykmeldingerWithSporsmal(fnr, true, fullBehandler)
            .filter(filterIncludeAndExclude(include, exclude))
            .filter(filterFomDate(fom))
            .filter(filterTomDate(tom))
    }

    suspend fun getSykmeldtStatusForDato(fnr: String, dato: LocalDate): SykmeldtStatus {
        val sykmeldinger = getInternalSykmeldinger(fnr, dato, dato)
        return if (sykmeldinger.isEmpty()) {
            SykmeldtStatus(erSykmeldt = false, gradert = null, fom = null, tom = null)
        } else {
            val sykmelding = sykmeldinger.first()
            SykmeldtStatus(
                erSykmeldt = true,
                gradert = inneholderGradertPeriode(sykmelding.sykmeldingsperioder),
                fom = finnForsteFom(sykmelding.sykmeldingsperioder),
                tom = finnSisteTom(sykmelding.sykmeldingsperioder)
            )
        }
    }

    fun inneholderGradertPeriode(perioder: List<SykmeldingsperiodeDTO>): Boolean {
        return perioder.firstOrNull { it.gradert != null }?.gradert != null
    }

    private fun finnForsteFom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.minByOrNull { it.fom }?.fom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten fom")
    }

    private fun finnSisteTom(perioder: List<SykmeldingsperiodeDTO>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }

    private fun filterIncludeAndExclude(
        include: List<String>?,
        exclude: List<String>?
    ): (SykmeldingDTO) -> Boolean {
        return {
            if (!include.isNullOrEmpty()) {
                include.contains(it.sykmeldingStatus.statusEvent)
            } else if (!exclude.isNullOrEmpty()) {
                !exclude.contains(it.sykmeldingStatus.statusEvent)
            } else {
                true
            }
        }
    }

    suspend fun getSykmeldingMedId(sykmeldingId: String): SykmeldingDTO? =
        withContext(Dispatchers.IO) {
            database.getSykmeldingerMedId(sykmeldingId)?.let {
                it.toSykmeldingDTO(
                    sporsmal = getSporsmal(it),
                    isPasient = false,
                    ikkeTilgangTilDiagnose = true
                )
            }
        }

    suspend fun getSykInnSykmeldingMedId(sykmeldingId: String): SykInnSykmeldingDTO? =
        withContext(Dispatchers.IO) { database.getSykInnSykmeldingerMedId(sykmeldingId) }


    suspend fun getSykInnSykmeldingForIdent(ident: String): List<SykInnSykmeldingDTO?> =
        withContext(Dispatchers.IO) { database.getSykInnSykmeldingForIdent(ident) }


    suspend fun getSykmelding(
        sykmeldingId: String,
        fnr: String,
        fullBehandler: Boolean = false
    ): SykmeldingDTO? =
        withContext(Dispatchers.IO) {
            database.getSykmelding(sykmeldingId, fnr)?.let {
                it.toSykmeldingDTO(
                    sporsmal = getSporsmal(it),
                    isPasient = true,
                    ikkeTilgangTilDiagnose = it.sykmeldingsDokument.skjermesForPasient,
                    fullBehandler = fullBehandler
                )
            }
        }

    private suspend fun getSykmeldingerWithSporsmal(
        fnr: String,
        isPasient: Boolean = false,
        fullBehandler: Boolean = true
    ): List<SykmeldingDTO> {
        return withContext(Dispatchers.IO) {
            database.getSykmeldinger(fnr).map {
                it.toSykmeldingDTO(
                    sporsmal = getSporsmal(it),
                    isPasient = isPasient,
                    ikkeTilgangTilDiagnose = false,
                    fullBehandler = fullBehandler
                )
            }
        }
    }

    private suspend fun getSporsmal(sykmelding: SykmeldingDbModel): List<Sporsmal> {
        return withContext(Dispatchers.IO) {
            val sporsmal =
                when {
                    sykmelding.status.statusEvent == "SENDT" ||
                        sykmelding.status.statusEvent == "BEKREFTET" ->
                        database.hentSporsmalOgSvar(sykmelding.id)
                    else -> emptyList()
                }
            sporsmal
        }
    }

    private fun filterTomDate(tom: LocalDate?): (SykmeldingDTO) -> Boolean {
        return {
            tom?.let { tomDate ->
                it.sykmeldingsperioder.any { sykmeldingsperiodeDTO ->
                    tomDate >= sykmeldingsperiodeDTO.fom
                }
            }
                ?: true
        }
    }

    private fun filterFomDate(fom: LocalDate?): (SykmeldingDTO) -> Boolean {
        return {
            fom?.let { fomDate ->
                it.sykmeldingsperioder.any { sykmeldingsperiodeDTO ->
                    fomDate <= sykmeldingsperiodeDTO.tom
                }
            }
                ?: true
        }
    }
}
