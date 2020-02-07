package no.nav.syfo.sykmelding.internal.model

import java.time.LocalDate
import no.nav.syfo.aksessering.api.PeriodetypeDTO

data class SykmeldingsperiodeDTO(
    val fom: LocalDate,
    val tom: LocalDate,
    val gradert: GradertDTO?,
    val behandlingsdager: Int?,
    val innspillTilArbeidsgiver: String?,
    val type: PeriodetypeDTO,
    val aktivitetIkkeMulig: AktivitetIkkeMuligDTO?,
    val reisetilskudd: Boolean
)
