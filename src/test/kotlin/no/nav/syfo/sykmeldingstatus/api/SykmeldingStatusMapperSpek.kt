package no.nav.syfo.sykmeldingstatus.api

import java.time.LocalDateTime
import no.nav.syfo.sykmeldingstatus.ArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.ShortName
import no.nav.syfo.sykmeldingstatus.Sporsmal
import no.nav.syfo.sykmeldingstatus.Svar
import no.nav.syfo.sykmeldingstatus.Svartype
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusMapperSpek : Spek({
    val sykmeldingId = "id"

    describe("Test av tilSykmeldingSendEvent") {
            it("Mapper sykmeldingSendEventDTO riktig") {
                val timestamp = LocalDateTime.now()
                val sykmeldingSendEventDTO = SykmeldingSendEventDTO(
                    timestamp,
                    ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
                    listOf(SporsmalOgSvarDTO("Arbeidssituasjon", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
                        SporsmalOgSvarDTO("Nærmeste leder", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI"))
                )

                val sykmeldingSendEvent = tilSykmeldingSendEvent(sykmeldingId, sykmeldingSendEventDTO)

                sykmeldingSendEvent.sykmeldingId shouldEqual sykmeldingId
                sykmeldingSendEvent.timestamp shouldEqual timestamp
                sykmeldingSendEvent.sporsmal shouldEqual Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))
                sykmeldingSendEvent.arbeidsgiver shouldEqual ArbeidsgiverStatus(sykmeldingId, "orgnummer", null, "navn")
            }
    }

    describe("Test av tilSykmeldingBekreftEvent") {
        it("Mapper sykmeldingBekreftEventDTO med spørsmål riktig") {
            val timestamp = LocalDateTime.now()
            val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())

            val sykmeldingBekreftEvent = tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTO)

            sykmeldingBekreftEvent.sykmeldingId shouldEqual sykmeldingId
            sykmeldingBekreftEvent.timestamp shouldEqual timestamp
            sykmeldingBekreftEvent.sporsmal?.size shouldEqual 4
            sykmeldingBekreftEvent.sporsmal!![0] shouldEqual Sporsmal("Sykmeldt fra ", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "Frilanser"))
            sykmeldingBekreftEvent.sporsmal!![1] shouldEqual Sporsmal("Har forsikring?", ShortName.FORSIKRING, Svar(sykmeldingId, null, Svartype.JA_NEI, "Ja"))
            sykmeldingBekreftEvent.sporsmal!![2] shouldEqual Sporsmal("Hatt fravær?", ShortName.FRAVAER, Svar(sykmeldingId, null, Svartype.JA_NEI, "Ja"))
            sykmeldingBekreftEvent.sporsmal!![3] shouldEqual Sporsmal("Når hadde du fravær?", ShortName.PERIODE, Svar(sykmeldingId, null, Svartype.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
        }

        it("Mapper sykmeldingBekreftEventDTO uten spørsmål riktig") {
            val timestamp = LocalDateTime.now()
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, null)

            val sykmeldingBekreftEventUtenSpm = tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTOUtenSpm)

            sykmeldingBekreftEventUtenSpm.sykmeldingId shouldEqual sykmeldingId
            sykmeldingBekreftEventUtenSpm.timestamp shouldEqual timestamp
            sykmeldingBekreftEventUtenSpm.sporsmal shouldEqual null
        }

        it("Mapper sykmeldingBekreftEventDTO med tom spørsmålsliste riktig") {
            val timestamp = LocalDateTime.now()
            val sykmeldingBekreftEventDTOUtenSpm = SykmeldingBekreftEventDTO(timestamp, emptyList())

            val sykmeldingBekreftEventUtenSpm = tilSykmeldingBekreftEvent(sykmeldingId, sykmeldingBekreftEventDTOUtenSpm)

            sykmeldingBekreftEventUtenSpm.sykmeldingId shouldEqual sykmeldingId
            sykmeldingBekreftEventUtenSpm.timestamp shouldEqual timestamp
            sykmeldingBekreftEventUtenSpm.sporsmal shouldEqual null
        }
    }
})

fun lagSporsmalOgSvarDTOListe(): List<SporsmalOgSvarDTO> {
    return listOf(SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser"),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
}
