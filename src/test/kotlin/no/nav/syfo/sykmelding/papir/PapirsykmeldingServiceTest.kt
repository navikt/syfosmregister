package no.nav.syfo.sykmelding.papir

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.papir.db.getPapirsykmelding
import no.nav.syfo.testutil.getPapirsykmeldingDbModel
import org.amshove.kluent.shouldNotBe

class PapirsykmeldingServiceTest :
    FunSpec({
        val database = mockkClass(DatabaseInterface::class)
        val sykmeldingerService = PapirsykmeldingService(database)

        beforeTest { mockkStatic("no.nav.syfo.sykmelding.papir.db.PapirsykmeldingDbKt") }

        afterTest { clearAllMocks() }
        context("Test Papirsykmelding API") {
            test("Should return a papirsykmelding") {
                every { database.getPapirsykmelding(any()) } returns getPapirsykmeldingDbModel()
                val papirsykmelding = sykmeldingerService.getPapirsykmelding("1234")
                papirsykmelding shouldNotBe null
            }
        }
    })
