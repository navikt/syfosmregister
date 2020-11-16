package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.Gradert
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.db.getSykmeldingerMedId
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.serviceuser.api.model.SykmeldtStatus
import no.nav.syfo.testutil.getGradertePerioder
import no.nav.syfo.testutil.getPeriode
import no.nav.syfo.testutil.getPerioder
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
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger.size shouldEqual 0
        }

        it("should filter include statuses") {
            every { database.getSykmeldinger(any()) } returns listOf(
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("APEN", OffsetDateTime.now(), null)),
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("AVBRUTT", OffsetDateTime.now(), null))
                    )

            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null, listOf("AVBRUTT"), null)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldEqual "AVBRUTT"
        }

        it("should filter multiple include statuses") {
            every { database.getSykmeldinger(any()) } returns listOf(
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("APEN", OffsetDateTime.now(), null)),
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("AVBRUTT", OffsetDateTime.now(), null))
            )

            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null, listOf("AVBRUTT", "APEN"), null)
            sykmeldinger.size shouldEqual 2
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldEqual "APEN"
            sykmeldinger[1].sykmeldingStatus.statusEvent shouldEqual "AVBRUTT"
        }

        it("should filter exclude statuses") {
            every { database.getSykmeldinger(any()) } returns listOf(
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("APEN", OffsetDateTime.now(), null)),
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("AVBRUTT", OffsetDateTime.now(), null))
            )

            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null, null, listOf("AVBRUTT"))
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldEqual "APEN"
        }

        it("should filter multiple exclude statuses") {
            every { database.getSykmeldinger(any()) } returns listOf(
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("APEN", OffsetDateTime.now(), null)),
                    getSykmeldingerDBmodel().copy(status = StatusDbModel("AVBRUTT", OffsetDateTime.now(), null))
            )

            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null, null, listOf("AVBRUTT", "APEN"))
            sykmeldinger.size shouldEqual 0
        }

        it("Should get list of sykmeldinger for user") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel())
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger.size shouldEqual 1
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldEqual false
        }

        it("Should not get medisinsk vurderering når sykmeldingen skal skjermes for pasient") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(skjermet = true))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
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
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodelEgenmeldt(hovediagnosekode = "R991", perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual true
        }

        it("harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har bidiganose med diagnosekode U071 som hoveddiagnose") {
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodelEgenmeldt(bidiagnoser = listOf(Diagnose("system", "U071", "tekst")), perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual true
        }

        it("harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har bidiganose med diagnosekode U072 som hoveddiagnose") {
            every { database.getSykmeldingerMedId(any()) } returns getSykmeldingerDBmodelEgenmeldt(bidiagnoser = listOf(Diagnose("system", "U072", "tekst")), perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldEqual null
            sykmelding.harRedusertArbeidsgiverperiode shouldEqual true
        }

        it("Skal hente sykmeldinger som er er innenfor FOM og TOM") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 1))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare fom som er før sykmeldingsperioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 9), null)
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare fom som er midt i perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 16), null)
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare fom som er i slutten av perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 20), null)
            sykmeldinger.size shouldEqual 1
        }
        it("Skal ikke hente sykmeldinger med bare fom som er i etter perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 21), null)
            sykmeldinger.size shouldEqual 0
        }

        it("Skal ikke hente sykmeldinger med bare fom som er innenfor en av periodene") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ), getPeriode(
                    fom = LocalDate.of(2020, 2, 21),
                    tom = LocalDate.of(2020, 2, 21)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 21), null)
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare TOM som er etter perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 21))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare TOM som er i slutten av perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 20))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare TOM som er i midten av perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 15))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med bare TOM som er i starten av perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 10))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal ikke hente sykmeldinger med bare TOM som er før perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 9))
            sykmeldinger.size shouldEqual 0
        }
        it("Skal hente sykmeldinger med bare TOM som er innenfor en av periodene") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ), getPeriode(
                    fom = LocalDate.of(2020, 2, 5),
                    tom = LocalDate.of(2020, 2, 9)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, LocalDate.of(2020, 2, 9))
            sykmeldinger.size shouldEqual 1
        }

        it("Skal hente sykmeldinger med FOM og TOM inni en periode") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 11), LocalDate.of(2020, 2, 19))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med FOM og TOM, der fom er før perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2019, 2, 11), LocalDate.of(2020, 2, 19))
            sykmeldinger.size shouldEqual 1
        }
        it("Skal hente sykmeldinger med FOM og TOM, der fom er etter perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2019, 2, 20), LocalDate.of(2020, 2, 28))
            sykmeldinger.size shouldEqual 1
        }

        it("Skal ikke hente sykmeldinger med FOM og TOM, der de er før perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2019, 2, 5), LocalDate.of(2020, 2, 9))
            sykmeldinger.size shouldEqual 0
        }
        it("Skal ikke hente sykmeldinger med FOM og TOM, der de er etter perioden") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20)
            ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 21), LocalDate.of(2020, 2, 28))
            sykmeldinger.size shouldEqual 0
        }

        it("Skal hente sykmeldinger med FOM og TOM, der de passer minst en periode") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(
                    getPeriode(
                            fom = LocalDate.of(2020, 2, 10),
                            tom = LocalDate.of(2020, 2, 20)),
                    getPeriode(
                            fom = LocalDate.of(2020, 2, 21),
                            tom = LocalDate.of(2020, 2, 21)
                    ))))
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, LocalDate.of(2020, 2, 21), LocalDate.of(2020, 2, 28))
            sykmeldinger.size shouldEqual 1
        }
    }

    describe("Test av sykmeldtStatus") {
        it("Skal få sykmeldt = true hvis sykmeldt på gitt dato (fom)") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 2, 10),
                tom = LocalDate.of(2020, 2, 20),
                gradert = Gradert(false, 50)
            ))))

            val sykmeldtStatus = sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 10))

            sykmeldtStatus shouldEqual SykmeldtStatus(erSykmeldt = true, gradert = true, fom = LocalDate.of(2020, 2, 10), tom = LocalDate.of(2020, 2, 20))
        }
        it("Skal få sykmeldt = true hvis sykmeldt på gitt dato (tom)") {
            every { database.getSykmeldinger(any()) } returns listOf(
                getSykmeldingerDBmodel(perioder = listOf(
                    getPeriode(
                        fom = LocalDate.of(2020, 2, 10),
                        tom = LocalDate.of(2020, 2, 20)))),
                getSykmeldingerDBmodel(perioder = listOf(
                    getPeriode(
                        fom = LocalDate.of(2019, 2, 10),
                        tom = LocalDate.of(2019, 2, 20),
                        gradert = Gradert(false, 50)
                    ))))

            val sykmeldtStatus = sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 20))

            sykmeldtStatus shouldEqual SykmeldtStatus(erSykmeldt = true, gradert = false, fom = LocalDate.of(2020, 2, 10), tom = LocalDate.of(2020, 2, 20))
        }
        it("Skal få sykmeldt = false hvis ikke sykmeldt på gitt dato (fom)") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 2, 10),
                tom = LocalDate.of(2020, 2, 20)
            ))))

            val sykmeldtStatus = sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 9))

            sykmeldtStatus shouldEqual SykmeldtStatus(erSykmeldt = false, gradert = null, fom = null, tom = null)
        }
        it("Skal få sykmeldt = false hvis ikke sykmeldt på gitt dato (tom)") {
            every { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 2, 10),
                tom = LocalDate.of(2020, 2, 20)
            ))))

            val sykmeldtStatus = sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 21))

            sykmeldtStatus shouldEqual SykmeldtStatus(erSykmeldt = false, gradert = null, fom = null, tom = null)
        }
        it("Skal få inneholderGradertPeriode = true hvis inneholder en periode med gradering") {
            val perioder = mutableListOf<SykmeldingsperiodeDTO>()
            perioder.addAll(getPerioder())
            perioder.addAll(getGradertePerioder())

            val inneholderGradertPeriode = sykmeldingerService.inneholderGradertPeriode(perioder)

            inneholderGradertPeriode shouldEqual true
        }
        it("Skal få inneholderGradertPeriode = false hvis ingen perioder er gradert") {
            val perioder = mutableListOf<SykmeldingsperiodeDTO>()
            perioder.addAll(getPerioder())

            val inneholderGradertPeriode = sykmeldingerService.inneholderGradertPeriode(perioder)

            inneholderGradertPeriode shouldEqual false
        }
        it("Skal få tidligste fom og seneste tom hvis sykmelding har flere perioder") {
            every { database.getSykmeldinger(any()) } returns listOf(
                getSykmeldingerDBmodel(perioder = listOf(
                    getPeriode(
                        fom = LocalDate.of(2020, 2, 8),
                        tom = LocalDate.of(2020, 2, 15)),
                    getPeriode(
                        fom = LocalDate.of(2020, 2, 16),
                        tom = LocalDate.of(2020, 2, 25),
                        gradert = Gradert(false, 50)
                    ))),
                getSykmeldingerDBmodel(perioder = listOf(
                    getPeriode(
                        fom = LocalDate.of(2019, 2, 10),
                        tom = LocalDate.of(2019, 2, 20),
                        gradert = Gradert(false, 50)
                    )))
            )

            val sykmeldtStatus = sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 20))

            sykmeldtStatus shouldEqual SykmeldtStatus(erSykmeldt = true, gradert = true, fom = LocalDate.of(2020, 2, 8), tom = LocalDate.of(2020, 2, 25))
        }
    }
})
