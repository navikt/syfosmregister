package no.nav.syfo.juridiskvurdering

import java.time.LocalDate
import java.time.ZonedDateTime

data class JuridiskVurderingResult(
    val juridiskeVurderinger: List<JuridiskVurdering>,
)

enum class JuridiskUtfall {
    VILKAR_OPPFYLT,
    VILKAR_IKKE_OPPFYLT,
    VILKAR_UAVKLART,
    VILKAR_BEREGNET
}

enum class Lovverk(val navn: String, val kortnavn: String, val lovverksversjon: LocalDate) {
    FOLKETRYGDLOVEN(
        navn = "Lov om folketrygd",
        kortnavn = "Folketrygdloven",
        lovverksversjon = LocalDate.of(2022, 1, 1)
    ),
    FORVALTNINGSLOVEN(
        navn = "Lov om behandlingsmåten i forvaltningssaker",
        kortnavn = "Forvaltningsloven",
        lovverksversjon = LocalDate.of(2022, 1, 1)
    ),
    HELSEPERSONELLOVEN(
        navn = "Lov om helsepersonell m.v.",
        kortnavn = "Helsepersonelloven",
        lovverksversjon = LocalDate.of(2022, 1, 1)
    )
}

data class JuridiskHenvisning(
    val lovverk: Lovverk,
    val paragraf: String,
    val ledd: Int?,
    val punktum: Int?,
    val bokstav: String?,
)

data class JuridiskVurdering(
    val id: String,
    val eventName: String,
    val version: String,
    val kilde: String,
    val versjonAvKode: String,
    val fodselsnummer: String,
    val juridiskHenvisning: JuridiskHenvisning,
    val sporing: Map<String, String>,
    val input: Map<String, Any>,
    val tidsstempel: ZonedDateTime,
    val utfall: JuridiskUtfall
)

data class TilbakedateringInputs(
    val fom: LocalDate,
    val tom: LocalDate?,
    val genereringstidspunkt: LocalDate,
    val ettersendingAv: String?,
    val forlengelseAv: String?,
    val syketilfelletStartdato: LocalDate?,
    val arbeidsgiverperiode: Boolean?,
    val diagnoseSystem: String?,
    val diagnoseKode: String?,
)

fun Map<String, Any>.toTilbakedateringInputs(): TilbakedateringInputs {
    val hoveddiag = this["hoveddiagnose"] as Map<String, String>?
    return TilbakedateringInputs(
        fom = LocalDate.parse(this["fom"] as String),
        tom = (this["tom"] as String?)?.let { LocalDate.parse(it) },
        genereringstidspunkt = LocalDate.parse(this["genereringstidspunkt"] as String),
        ettersendingAv = this["ettersendingAv"] as String?,
        forlengelseAv =
            (this["forlengelseAv"] as List<Map<String, String>>?)?.let {
                it.first()["sykmeldingId"] as String
            },
        syketilfelletStartdato =
            (this["syketilfelletStartdato"] as String?)?.let { LocalDate.parse(it) },
        arbeidsgiverperiode = this["arbeidsgiverperiode"] as Boolean?,
        diagnoseSystem = hoveddiag?.let { it["system"] },
        diagnoseKode =  hoveddiag?.let { it["kode"] },
    )
}
