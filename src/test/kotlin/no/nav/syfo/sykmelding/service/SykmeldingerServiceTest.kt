package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.testutil.getSykmeldingerDBmodel
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingerServiceTest : Spek({
    val sykmeldingId = "123"
    val database = mockkClass(DatabaseInterface::class)
    val sykmeldingerService = SykmeldingerService(database)

    beforeEachTest {
        mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingQueriesKt")
        every { database.getSykmeldinger(any()) } returns emptyList()
    }

    afterEachTest {
        clearAllMocks()
    }

    describe("Test SykmeldingerService") {
        it("Should get 0 sykmeldinger as user") {
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId)
            sykmeldinger.size shouldEqual 0
        }

        it("Should get list of sykmeldinger for user") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel())
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].medisinskVurdering shouldNotBe null
        }

        it("Should not get medisinsk vurderering når sykmeldingen skal skjermes for pasient") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(skjermet = true))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId)
            sykmeldinger[0].medisinskVurdering shouldBe null
        }

        it("should get internalSykmellding") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(skjermet = true))
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].medisinskVurdering shouldNotBe null
        }

    }
})
