package no.nav.syfo.sykmelding.user.api

import java.time.LocalDate
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO

fun getSykmeldingperiodeDto(fom: LocalDate, tom: LocalDate): SykmeldingsperiodeDTO {
    return SykmeldingsperiodeDTO(
            fom = fom,
            tom = tom,
            reisetilskudd = false,
            behandlingsdager = null,
            gradert = null,
            aktivitetIkkeMulig = null,
            innspillTilArbeidsgiver = null,
            type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG
    )
}
