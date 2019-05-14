package no.nav.syfo.model

import io.mockk.every
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate.of
import java.time.LocalDateTime.of
import kotlin.test.assertEquals

object BrukerSykmeldingSpek : Spek({
    val mock = mockk<ResultSet>()

    describe("Brukersykmelding domain object mapper") {
        it("should be able to parse a simple resultSet list") {
            val behandlingsutfallJson = "" +
                    "{\"status\": \"INVALID\", " +
                    "\"ruleHits\": [{\"ruleName\": \"TOO_MANY_TREATMENT_DAYS\", " +
                    "\"messageForUser\": \"text\", " +
                    "\"messageForSender\": \"text\"}]}"
            val perioderJson = "" +
                    "[{\"fom\": \"2018-01-01\", \"tom\": \"2018-01-20\", \"grad\": 80}, " +
                    "{\"fom\": \"2018-02-01\", \"tom\": \"2018-02-20\"}]"
            every { mock.getString("id") } returns "id"
            every {
                mock.getTimestamp("bekreftet_dato")
            } returns Timestamp.valueOf(of(2019, 1, 1, 0, 0))
            every { mock.getString("behandlings_utfall") } returns behandlingsutfallJson
            every { mock.getString("legekontor_org_nr") } returns "orgnr"
            every { mock.getString("lege_fornavn") } returns "Fornavn"
            every { mock.getString("lege_mellomnavn") } returns "Mellomnavn"
            every { mock.getString("lege_etternavn") } returns "Etternavn"
            every { mock.getString("arbeidsgivernavn") } returns "arbeidsgivernavn"
            every { mock.getString("perioder") } returns perioderJson

            val expectedPerioder: List<Sykmeldingsperiode> = listOf(
                Sykmeldingsperiode(of(2018, 1, 1), of(2018, 1, 20), 80),
                Sykmeldingsperiode(of(2018, 2, 1), of(2018, 2, 20), null)
            )

            val brukerSykmelding = brukerSykmeldingFromResultSet(mock)
            assertEquals(expectedPerioder, brukerSykmelding.sykmeldingsperioder)
        }

        it("should be able to parse legenavn withut mellomnavn") {
            every { mock.getString("lege_fornavn") } returns "Fornavn"
            every { mock.getString("lege_mellomnavn") } returns null
            every { mock.getString("lege_etternavn") } returns "Etternavn"

            assertEquals("Fornavn Etternavn", getLegenavn(mock))
        }

        it("should be able to parse legenavn withut any names") {
            every { mock.getString("lege_fornavn") } returns null
            every { mock.getString("lege_mellomnavn") } returns null
            every { mock.getString("lege_etternavn") } returns null

            assertEquals(null, getLegenavn(mock))
        }

        it("should be able to parse legenavn whith fornavn, mellomnavn and etternavn") {
            every { mock.getString("lege_fornavn") } returns "Fornavn"
            every { mock.getString("lege_mellomnavn") } returns "Mellomnavn"
            every { mock.getString("lege_etternavn") } returns "Etternavn"

            assertEquals("Fornavn Mellomnavn Etternavn", getLegenavn(mock))
        }
    }
})
