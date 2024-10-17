package no.nav.syfo.sykmelding.papir

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.papir.db.getPapirsykmelding
import no.nav.syfo.testutil.getPapirsykmeldingDbModel
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PapirsykmeldingServiceTest {
    val database = mockkClass(DatabaseInterface::class)
    val sykmeldingerService = PapirsykmeldingService(database)

    @BeforeEach
    fun beforeTest() {
        mockkStatic("no.nav.syfo.sykmelding.papir.db.PapirsykmeldingDbKt")
    }

    @AfterEach
    fun afterTest() {
        clearAllMocks()
    }

    @Test
    internal fun `Test Papirsykmelding API Should return a papirsykmelding`() {
        every { database.getPapirsykmelding(any()) } returns getPapirsykmeldingDbModel()
        val papirsykmelding = sykmeldingerService.getPapirsykmelding("1234")
        papirsykmelding shouldNotBe null
    }
}
