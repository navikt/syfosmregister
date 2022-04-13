package no.nav.syfo.sykmelding.papir

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.papir.db.getPapirsykmelding
import no.nav.syfo.testutil.getPapirsykmeldingDbModel
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PapirsykmeldingServiceTest : Spek({
    val database = mockkClass(DatabaseInterface::class)
    val sykmeldingerService = PapirsykmeldingService(database)

    beforeEachTest {
        mockkStatic("no.nav.syfo.sykmelding.papir.db.PapirsykmeldingDbKt")
    }

    afterEachTest {
        clearAllMocks()
    }
    describe("Test Papirsykmelding API") {

        it("Should return a papirsykmelding") {
            every { database.getPapirsykmelding(any()) } returns getPapirsykmeldingDbModel()
            val papirsykmelding = sykmeldingerService.getPapirsykmelding("1234")
            papirsykmelding shouldNotBe null
        }
    }
})
