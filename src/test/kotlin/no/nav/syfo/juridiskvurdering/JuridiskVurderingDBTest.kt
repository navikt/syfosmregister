package no.nav.syfo.juridiskvurdering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import java.util.UUID
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import org.amshove.kluent.shouldBeEqualTo

class JuridiskVurderingDBTest :
    FunSpec(
        {
            val db = TestDB.database
            val juridiskVurderingDb = JuridiskVurderingDB(db)
            afterTest { db.connection.dropData() }

            afterSpec { TestDB.stop() }

            context("Test at JuridiskVurdering persisteres og hentes ut riktig") {
                val sykmeldingsId = UUID.randomUUID().toString()
                val vurdering = objectMapper.readValue<JuridiskVurdering>(jsonVurdering)
                val minimalVurdering =
                    objectMapper.readValue<JuridiskVurdering>(jsonVurderingMinimal)
                val withINcorrectTime =
                    objectMapper.readValue<JuridiskVurdering>(jsonVurderingNotCorretTime)

                val nyVurdering = objectMapper.readValue<JuridiskVurdering>(jsonNyVersjon)
                juridiskVurderingDb.insertOrUpdate(
                    withINcorrectTime,
                    withINcorrectTime.input.toTilbakedateringInputs()
                )
                juridiskVurderingDb.insertOrUpdate(
                    minimalVurdering,
                    minimalVurdering.input.toTilbakedateringInputs()
                )
                juridiskVurderingDb.insertOrUpdate(
                    vurdering,
                    vurdering.input.toTilbakedateringInputs()
                )

                val inputs = vurdering.input.toTilbakedateringInputs()
                val forlengelseAv = inputs.forlengelseAv
                val ettersending = inputs.ettersendingAv

                forlengelseAv shouldBeEqualTo "fab84f88-97cb-43ca-a8c0-8eaf2564734a"
                ettersending shouldBeEqualTo "adsf"

                juridiskVurderingDb.insertOrUpdate(
                    nyVurdering,
                    nyVurdering.input.toTilbakedateringInputs(),
                )

                val nyInputs = nyVurdering.input.toTilbakedateringInputs()
                val nyForlengelseAv = nyInputs.forlengelseAv
                val nyEttersending = nyInputs.ettersendingAv

                nyForlengelseAv shouldBeEqualTo "fab84f88-97cb-43ca-a8c0-8eaf2564734a"
                nyEttersending shouldBeEqualTo "abcd"
            }
        },
    )

val jsonVurderingNotCorretTime =
    """
    {
      "id": "dfa3e87a-17e8-4819-9bee-24a7ce722f67",
      "eventName": "subsumsjon",
      "version": "1.0.0",
      "kilde": "syfosmregler",
      "versjonAvKode": "https://github.com/navikt/syfosmregler/tree/151a7363f680608b8bed824b5600d9553372295d",
      "fodselsnummer": "123",
      "juridiskHenvisning": {
        "lovverk": "FOLKETRYGDLOVEN",
        "paragraf": "8-7",
        "ledd": 2,
        "punktum": null,
        "bokstav": null
      },
      "sporing": {
        "sykmelding": "12345"
      },
      "input": {
        "fom": "2025-01-05",
        "genereringstidspunkt": "2025-02-04"
      },
      "tidsstempel": "2023-08-21T11:48:34.280790096",
      "utfall": "VILKAR_OPPFYLT"
    }
"""
        .trimIndent()

val jsonVurderingMinimal =
    """
    {
      "id": "dfa3e87a-17e8-4819-9bee-24a7ce722f67",
      "eventName": "subsumsjon",
      "version": "1.0.0",
      "kilde": "syfosmregler",
      "versjonAvKode": "https://github.com/navikt/syfosmregler/tree/151a7363f680608b8bed824b5600d9553372295d",
      "fodselsnummer": "123",
      "juridiskHenvisning": {
        "lovverk": "FOLKETRYGDLOVEN",
        "paragraf": "8-7",
        "ledd": 2,
        "punktum": null,
        "bokstav": null
      },
      "sporing": {
        "sykmelding": "12345"
      },
      "input": {
        "fom": "2025-01-05",
        "genereringstidspunkt": "2025-02-04"
      },
      "tidsstempel": "2025-02-04T13:36:17.061501826Z",
      "utfall": "VILKAR_OPPFYLT"
    }
"""
        .trimIndent()

val jsonVurdering =
    """
    {
      "id": "dfa3e87a-17e8-4819-9bee-24a7ce722f67",
      "eventName": "subsumsjon",
      "version": "1.0.0",
      "kilde": "syfosmregler",
      "versjonAvKode": "https://github.com/navikt/syfosmregler/tree/151a7363f680608b8bed824b5600d9553372295d",
      "fodselsnummer": "123",
      "juridiskHenvisning": {
        "lovverk": "FOLKETRYGDLOVEN",
        "paragraf": "8-7",
        "ledd": 2,
        "punktum": null,
        "bokstav": null
      },
      "sporing": {
        "sykmelding": "1234"
      },
      "input": {
        "fom": "2025-01-05",
        "genereringstidspunkt": "2025-02-04",
        "ettersending": true,
        "ettersendingAv": "adsf",
        "begrunnelse": "adsf adf adsf adf ",
        "forlengelse": true,
        "forlengelseAv": [
        {
          "sykmeldingId": "fab84f88-97cb-43ca-a8c0-8eaf2564734a",
          "fom": "2023-09-12",
          "tom": "2023-09-18"
        },
        {
          "sykmeldingId": "a23622ba-0094-4c46-a115-23fe4ff985a1",
          "fom": "2023-09-12",
          "tom": "2023-09-18"
        },
        {
          "sykmeldingId": "c4d11b21-02d0-4cc3-a0d3-61d45b33cf7f",
          "fom": "2023-09-19",
          "tom": "2023-09-30"
        },
        {
          "sykmeldingId": "8e37a0f4-017d-455f-b45f-45787d5657c7",
          "fom": "2023-09-19",
          "tom": "2023-09-30"
        }
        ],
        "syketilfelletStartdato": "2025-01-05",
        "tom": "2025-01-06",
        "dagerForArbeidsgiverperiode": [
          "2025-01-05",
          "2025-01-06"
        ],
          "hoveddiagnose": {
            "system": "2.16.578.1.12.4.1.1.7110",
            "kode": "H100",
            "tekst": "Mukopurulent konjunktivitt"
          },
        "arbeidsgiverperiode": true
      },
      "tidsstempel": "2025-02-04T13:36:17.061501826Z",
      "utfall": "VILKAR_OPPFYLT"
    }
"""
        .trimIndent()

val jsonNyVersjon =
    """
    {
      "id": "dfa3e87a-17e8-4819-9bee-24a7ce722f67",
      "eventName": "subsumsjon",
      "version": "1.0.0",
      "kilde": "syfosmregler",
      "versjonAvKode": "https://github.com/navikt/syfosmregler/tree/151a7363f680608b8bed824b5600d9553372295d",
      "fodselsnummer": "123",
      "juridiskHenvisning": {
        "lovverk": "FOLKETRYGDLOVEN",
        "paragraf": "8-7",
        "ledd": 2,
        "punktum": null,
        "bokstav": null
      },
      "sporing": {
        "sykmelding": "1234"
      },
      "input": {
        "fom": "2025-01-05",
        "genereringstidspunkt": "2025-02-04",
        "ettersending": {
            "sykmeldingId": "abcd",
            "fom": "2025-01-05",
            "tom": "2025-01-06"
        },
        "begrunnelse": "3 ord",
        "forlengelse": {
          "sykmeldingId": "fab84f88-97cb-43ca-a8c0-8eaf2564734a",
          "fom": "2023-09-12",
          "tom": "2023-09-18"
        },
        "syketilfelletStartdato": "2025-01-05",
        "tom": "2025-01-06",
        "dagerForArbeidsgiverperiode": [
          "2025-01-05",
          "2025-01-06"
        ],
        "hoveddiagnoseMangler": false,
        "arbeidsgiverperiode": true
      },
      "tidsstempel": "2025-02-04T13:36:17.061501826Z",
      "utfall": "VILKAR_OPPFYLT"
    }
"""
        .trimIndent()
