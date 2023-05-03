package no.nav.syfo.sykmelding.user.api

import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import java.time.LocalDate

fun getSykmeldingperiodeDto(fom: LocalDate, tom: LocalDate): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
        fom = fom,
        tom = tom,
        reisetilskudd = false,
        behandlingsdager = null,
        gradert = null,
        aktivitetIkkeMulig = null,
        innspillTilArbeidsgiver = null,
        type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
    )
}
