package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.db.getSykmeldingerMedId
import no.nav.syfo.testutil.getSykmeldingerDBmodel
import no.nav.syfo.testutil.getSykmeldingerDBmodelEgenmeldt
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
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("Should not get medisinsk vurderering når sykmeldingen skal skjermes for pasient") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(skjermet = true))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId)
            sykmeldinger[0].medisinskVurdering shouldBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("should get internalSykmellding") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(skjermet = true))
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].egenmeldt shouldBe false
            sykmeldinger[0].papirsykmelding shouldBe false
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("should get egenmeldt = true when avsenderSystem.name = 'Egenmeldt'") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodelEgenmeldt(avsenderSystem = AvsenderSystem("Egenmeldt", "versjon")))
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].egenmeldt shouldBe true
            sykmeldinger[0].papirsykmelding shouldBe false
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("should get paprisykmelding = true when avsenderSystem.name = 'Paprisykmelding'") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodelEgenmeldt(avsenderSystem = AvsenderSystem("Papirsykmelding", "versjon")))
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].egenmeldt shouldBe false
            sykmeldinger[0].papirsykmelding shouldBe true
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("skal ikke få med medisinsk vurdering ved henting med id") {
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodel(skjermet = false)
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har diagnosekode R991 som hoveddiagnose") {
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodelEgenmeldt(hovediagnosekode = "R991")
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual true
        }

        it("harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har bidiganose med diagnosekode U071 som hoveddiagnose") {
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodelEgenmeldt(bidiagnoser = listOf(Diagnose("system", "U071", "tekst")))
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual true
        }
    }
})
