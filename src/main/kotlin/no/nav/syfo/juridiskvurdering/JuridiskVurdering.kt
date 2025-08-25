package no.nav.syfo.juridiskvurdering

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ZonedDateTimeDeserializer : JsonDeserializer<ZonedDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ZonedDateTime {
        val timestamp = p.text
        return try {
            ZonedDateTime.parse(timestamp)
        } catch (e: Exception) {
            val localDateTime = LocalDateTime.parse(timestamp)
            localDateTime.atZone(ZoneId.of("UTC"))
        }
    }
}

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
    @param:JsonDeserialize(using = ZonedDateTimeDeserializer::class) val tidsstempel: ZonedDateTime,
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
    val hoveddiag =
        when (this["hoveddiagnose"]) {
            is Map<*, *>? -> this["hoveddiagnose"] as Map<String, String>?
            else -> null
        }
    return TilbakedateringInputs(
        fom = LocalDate.parse(this["fom"] as String),
        tom = (this["tom"] as String?)?.let { LocalDate.parse(it) },
        genereringstidspunkt = LocalDate.parse(this["genereringstidspunkt"] as String),
        ettersendingAv = getEttersending(),
        forlengelseAv = getForlengelseAV(),
        syketilfelletStartdato =
            (this["syketilfelletStartdato"] as String?)?.let { LocalDate.parse(it) },
        arbeidsgiverperiode = this["arbeidsgiverperiode"] as Boolean?,
        diagnoseSystem = hoveddiag?.let { it["system"] } ?: this["diagnosesystem"] as? String,
        diagnoseKode = hoveddiag?.let { it["kode"] },
    )
}

private fun Map<String, Any>.getEttersending(): String? {
    val ettersending = this["ettersending"]
    return when (ettersending) {
        is Boolean -> if (ettersending) this["ettersendingAv"]?.toString() else null
        is Map<*, *> -> ettersending["sykmeldingId"] as? String
        else -> null
    }
}

private fun Map<String, Any>.getForlengelseAV(): String? {
    val forlengelse = this["forlengelse"]
    return when (forlengelse) {
        is Boolean -> {
            if (forlengelse) {
                (this["forlengelseAv"] as List<Map<String, String>>?)?.let {
                    it.first()["sykmeldingId"] as String
                }
            } else {
                null
            }
        }
        is Map<*, *> -> forlengelse["sykmeldingId"] as? String
        else -> null
    }
}
