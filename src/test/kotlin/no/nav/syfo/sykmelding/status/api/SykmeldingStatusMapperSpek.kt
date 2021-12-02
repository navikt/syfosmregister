package no.nav.syfo.sykmelding.status.api

import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.StatusEventDTO
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingStatus
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusMapperSpek : Spek({
    val sykmeldingId = "id"

    describe("Test av tilSykmeldingSendEvent") {
        it("Mapper sykmeldingSendEventDTO riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingSendEventDTO = SykmeldingSendEventDTO(
                timestamp,
                ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
                listOf(
                    SporsmalOgSvarDTO(
                        "Arbeidssituasjon",
                        ShortNameDTO.ARBEIDSSITUASJON,
                        SvartypeDTO.ARBEIDSSITUASJON,
                        "ARBEIDSTAKER"
                    ),
                    SporsmalOgSvarDTO("Nærmeste leder", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI")
                )
            )

            val sykmeldingSendEvent = tilSykmeldingSendEvent(sykmeldingId, sykmeldingSendEventDTO)

            sykmeldingSendEvent.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingSendEvent.timestamp shouldBeEqualTo timestamp
            sykmeldingSendEvent.sporsmal shouldBeEqualTo Sporsmal(
                "Arbeidssituasjon",
                ShortName.ARBEIDSSITUASJON,
                Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER")
            )
            sykmeldingSendEvent.arbeidsgiver shouldBeEqualTo ArbeidsgiverStatus(sykmeldingId, "orgnummer", null, "navn")
        }
    }

    describe("Test av tilSykmeldingBekreftEvent") {
        it("Mapper sykmeldingBekreftEventDTO med spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())

            val sykmeldingBekreftEvent = tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTO)

            sykmeldingBekreftEvent.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingBekreftEvent.timestamp shouldBeEqualTo timestamp
            sykmeldingBekreftEvent.sporsmal?.size shouldBeEqualTo 4
            sykmeldingBekreftEvent.sporsmal!![0] shouldBeEqualTo Sporsmal(
                "Sykmeldt fra ",
                ShortName.ARBEIDSSITUASJON,
                Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "Frilanser")
            )
            sykmeldingBekreftEvent.sporsmal!![1] shouldBeEqualTo Sporsmal(
                "Har forsikring?",
                ShortName.FORSIKRING,
                Svar(sykmeldingId, null, Svartype.JA_NEI, "Ja")
            )
            sykmeldingBekreftEvent.sporsmal!![2] shouldBeEqualTo Sporsmal(
                "Hatt fravær?",
                ShortName.FRAVAER,
                Svar(sykmeldingId, null, Svartype.JA_NEI, "Ja")
            )
            sykmeldingBekreftEvent.sporsmal!![3] shouldBeEqualTo Sporsmal(
                "Når hadde du fravær?",
                ShortName.PERIODE,
                Svar(
                    sykmeldingId,
                    null,
                    Svartype.PERIODER,
                    "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"
                )
            )
        }

        it("Mapper sykmeldingBekreftEventDTO uten spørsmål riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, null)

            val sykmeldingBekreftEventUtenSpm =
                tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTOUtenSpm)

            sykmeldingBekreftEventUtenSpm.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingBekreftEventUtenSpm.timestamp shouldBeEqualTo timestamp
            sykmeldingBekreftEventUtenSpm.sporsmal shouldBeEqualTo null
        }

        it("Mapper sykmeldingBekreftEventDTO med tom spørsmålsliste riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, emptyList())

            val sykmeldingBekreftEventUtenSpm =
                tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTOUtenSpm)

            sykmeldingBekreftEventUtenSpm.sykmeldingId shouldBeEqualTo sykmeldingId
            sykmeldingBekreftEventUtenSpm.timestamp shouldBeEqualTo timestamp
            sykmeldingBekreftEventUtenSpm.sporsmal shouldBeEqualTo null
        }
    }

    describe("Test av tilSykmeldingStatusDTO") {
        it("Mapper SykmeldingStatus for SENDT riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatus = SykmeldingStatus(
                timestamp,
                StatusEvent.SENDT,
                ArbeidsgiverStatus(
                    sykmeldingId = sykmeldingId,
                    orgnummer = "orgnummer",
                    juridiskOrgnummer = null,
                    orgnavn = "navn"
                ),
                listOf(
                    Sporsmal(
                        "Arbeidssituasjon",
                        ShortName.ARBEIDSSITUASJON,
                        Svar(sykmeldingId, 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER")
                    )
                )
            )

            val sykmeldingStatusDTO = tilSykmeldingStatusDTO(sykmeldingStatus)

            sykmeldingStatusDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusDTO.statusEvent shouldBeEqualTo StatusEventDTO.SENDT
            sykmeldingStatusDTO.sporsmalOgSvarListe?.size shouldBeEqualTo 1
            sykmeldingStatusDTO.sporsmalOgSvarListe!![0] shouldBeEqualTo SporsmalOgSvarDTO(
                "Arbeidssituasjon",
                ShortNameDTO.ARBEIDSSITUASJON,
                SvartypeDTO.ARBEIDSSITUASJON,
                "ARBEIDSTAKER"
            )
            sykmeldingStatusDTO.arbeidsgiver shouldBeEqualTo ArbeidsgiverStatusDTO("orgnummer", null, "navn")
        }

        it("Mapper SykmeldingStatus for BEKREFTET med spm/svar riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatus = SykmeldingStatus(
                timestamp,
                StatusEvent.BEKREFTET,
                null,
                lagSporsmalListe(sykmeldingId)
            )

            val sykmeldingStatusDTO = tilSykmeldingStatusDTO(sykmeldingStatus)

            sykmeldingStatusDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusDTO.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET
            sykmeldingStatusDTO.sporsmalOgSvarListe?.size shouldBeEqualTo 4
            sykmeldingStatusDTO.sporsmalOgSvarListe!![0] shouldBeEqualTo SporsmalOgSvarDTO(
                "Sykmeldt fra ",
                ShortNameDTO.ARBEIDSSITUASJON,
                SvartypeDTO.ARBEIDSSITUASJON,
                "Frilanser"
            )
            sykmeldingStatusDTO.sporsmalOgSvarListe!![1] shouldBeEqualTo SporsmalOgSvarDTO(
                "Har forsikring?",
                ShortNameDTO.FORSIKRING,
                SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusDTO.sporsmalOgSvarListe!![2] shouldBeEqualTo SporsmalOgSvarDTO(
                "Hatt fravær?",
                ShortNameDTO.FRAVAER,
                SvartypeDTO.JA_NEI,
                "Ja"
            )
            sykmeldingStatusDTO.sporsmalOgSvarListe!![3] shouldBeEqualTo SporsmalOgSvarDTO(
                "Når hadde du fravær?",
                ShortNameDTO.PERIODE,
                SvartypeDTO.PERIODER,
                "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"
            )
            sykmeldingStatusDTO.arbeidsgiver shouldBeEqualTo null
        }

        it("Mapper SykmeldingStatus for APEN riktig") {
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            val sykmeldingStatus = SykmeldingStatus(
                timestamp,
                StatusEvent.APEN,
                null,
                null
            )

            val sykmeldingStatusDTO = tilSykmeldingStatusDTO(sykmeldingStatus)

            sykmeldingStatusDTO.timestamp shouldBeEqualTo timestamp
            sykmeldingStatusDTO.statusEvent shouldBeEqualTo StatusEventDTO.APEN
            sykmeldingStatusDTO.sporsmalOgSvarListe shouldBeEqualTo null
            sykmeldingStatusDTO.arbeidsgiver shouldBeEqualTo null
        }
    }
})

fun lagSporsmalOgSvarDTOListe(): List<SporsmalOgSvarDTO> {
    return listOf(
        SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser"),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}")
    )
}

fun lagSporsmalListe(sykmeldingId: String): List<Sporsmal> {
    return listOf(
        Sporsmal("Sykmeldt fra ", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, 1, Svartype.ARBEIDSSITUASJON, "Frilanser")),
        Sporsmal("Har forsikring?", ShortName.FORSIKRING, Svar(sykmeldingId, 2, Svartype.JA_NEI, "Ja")),
        Sporsmal("Hatt fravær?", ShortName.FRAVAER, Svar(sykmeldingId, 3, Svartype.JA_NEI, "Ja")),
        Sporsmal("Når hadde du fravær?", ShortName.PERIODE, Svar(sykmeldingId, 4, Svartype.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
    )
}
