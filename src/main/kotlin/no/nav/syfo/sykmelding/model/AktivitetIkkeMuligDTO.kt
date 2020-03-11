package no.nav.syfo.sykmelding.model

data class AktivitetIkkeMuligDTO(
    val medisinskArsak: MedisinskArsakDTO?,
    val arbeidsrelatertArsak: ArbeidsrelatertArsakDTO?
)
