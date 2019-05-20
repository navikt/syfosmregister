package no.nav.syfo.model

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.aksessering.db.getLegenavn
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.ResultSet
import kotlin.test.assertEquals

object LegenavnSpek : Spek({
    val mock = mockk<ResultSet>()

    describe("Legenavn") {

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
