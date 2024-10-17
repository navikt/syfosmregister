package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import io.mockk.mockkStatic
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.Gradert
import no.nav.syfo.sykmelding.db.Merknad
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.db.getSykmeldingerMedId
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.serviceuser.api.model.SykmeldtStatus
import no.nav.syfo.testutil.getGradertePerioder
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import no.nav.syfo.testutil.getPeriode
import no.nav.syfo.testutil.getPerioder
import no.nav.syfo.testutil.getSykmeldingerDBmodel
import no.nav.syfo.testutil.getSykmeldingerDBmodelEgenmeldt
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SykmeldingerServiceTest {
    val sykmeldingId = "123"
    val database = mockkClass(DatabaseInterface::class)
    val sykmeldingerService = SykmeldingerService(database)

    @BeforeEach
    fun beforeTest() {
        mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingQueriesKt")
        coEvery { database.getSykmeldinger(any()) } returns emptyList()
    }

    @AfterEach
    fun afterTest() {
        clearAllMocks()
    }

    @Test
    internal fun `Test SykmeldingerService Should get 0 sykmeldinger as user`() {
        runBlocking {
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Test SykmeldingerService should filter include statuses`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    listOf("AVBRUTT"),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldBeEqualTo "AVBRUTT"
        }
    }

    @Test
    internal fun `Test SykmeldingerService getUserSykmelding should filter behandler fnr if fullBehandler = false`() {

        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )

        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    null,
                    null,
                    fullBehandler = false,
                )
            sykmeldinger.size shouldBeEqualTo 2
            sykmeldinger.first().behandler.fornavn shouldBeEqualTo "fornavn"
            sykmeldinger.first().behandler.fnr shouldBeEqualTo null
        }
    }

    @Test
    internal fun `Test SykmeldingerService getUserSykmelding should not filter behandler fnr if fullBehandler = true`() {

        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )

        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    null,
                    null,
                    fullBehandler = true,
                )
            sykmeldinger.size shouldBeEqualTo 2
            sykmeldinger.first().behandler.fornavn shouldBeEqualTo "fornavn"
            sykmeldinger.first().behandler.fnr shouldBeEqualTo "01234567891"
        }
    }

    @Test
    internal fun `Test SykmeldingerService should filter multiple include statuses`() {

        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    listOf("AVBRUTT", "APEN"),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 2
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldBeEqualTo "APEN"
            sykmeldinger[1].sykmeldingStatus.statusEvent shouldBeEqualTo "AVBRUTT"
        }
    }

    @Test
    internal fun `Test SykmeldingerService should filter exclude statuses`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )

        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    null,
                    listOf("AVBRUTT"),
                )
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].sykmeldingStatus.statusEvent shouldBeEqualTo "APEN"
        }
    }

    @Test
    internal fun `Test SykmeldingerService should filter multiple exclude statuses`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("APEN", getNowTickMillisOffsetDateTime(), null),
                    ),
                getSykmeldingerDBmodel()
                    .copy(
                        status = StatusDbModel("AVBRUTT", getNowTickMillisOffsetDateTime(), null),
                    ),
            )

        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    null,
                    null,
                    listOf("AVBRUTT", "APEN"),
                )
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Test SykmeldingerService Should get list of sykmeldinger for user`() {
        coEvery { database.getSykmeldinger(any()) } returns listOf(getSykmeldingerDBmodel())
        runBlocking {
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test SykmeldingerService Skal få med merknader`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel()
                    .copy(
                        merknader =
                            listOf(
                                Merknad(
                                    type = "UGYLDIG_TILBAKEDATERING",
                                    beskrivelse = null,
                                ),
                            ),
                    ),
            )
        runBlocking {
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
            sykmeldinger[0].merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
        }
    }

    @Test
    internal fun `Test SykmeldingerService Should not get medisinsk vurderering når sykmeldingen skal skjermes for pasient`() {

        coEvery { database.getSykmeldinger(any()) } returns
            listOf(getSykmeldingerDBmodel(skjermet = true))
        runBlocking {
            val sykmeldinger = sykmeldingerService.getUserSykmelding(sykmeldingId, null, null)
            sykmeldinger[0].medisinskVurdering shouldBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test SykmeldingerService should get internalSykmellding`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(getSykmeldingerDBmodel(skjermet = true))
        runBlocking {
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].egenmeldt shouldBe false
            sykmeldinger[0].papirsykmelding shouldBe false
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test SykmeldingerService should get egenmeldt true when avsenderSystem name 'Egenmeldt'`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodelEgenmeldt(
                    avsenderSystem =
                        AvsenderSystem(
                            "Egenmeldt",
                            "versjon",
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].egenmeldt shouldBe true
            sykmeldinger[0].papirsykmelding shouldBe false
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test SykmeldingerService should get paprisykmelding  true when avsenderSystem name 'Paprisykmelding'`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodelEgenmeldt(
                    avsenderSystem =
                        AvsenderSystem(
                            "Papirsykmelding",
                            "versjon",
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger = sykmeldingerService.getInternalSykmeldinger(sykmeldingId)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].egenmeldt shouldBe false
            sykmeldinger[0].papirsykmelding shouldBe true
            sykmeldinger[0].medisinskVurdering shouldNotBe null
            sykmeldinger[0].harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `Test SykmeldingerService skal ikke få med medisinsk vurdering ved henting med id`() {

        coEvery { database.getSykmeldingerMedId(any()) } returns
            getSykmeldingerDBmodel(skjermet = false)
        runBlocking {
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldBeEqualTo null
            sykmelding.harRedusertArbeidsgiverperiode shouldBeEqualTo false
        }
    }

    @Test
    internal fun `harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har diagnosekode R991 som hoveddiagnosed`() {

        coEvery { database.getSykmeldingerMedId(any()) } returns
            getSykmeldingerDBmodelEgenmeldt(
                hovediagnosekode = "R991",
                perioder =
                    listOf(
                        getPeriode(
                            fom = LocalDate.of(2020, 3, 10),
                            tom = LocalDate.of(2020, 3, 20),
                        ),
                    ),
            )
        runBlocking {
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldBeEqualTo null
            sykmelding.harRedusertArbeidsgiverperiode shouldBeEqualTo true
        }
    }

    @Test
    internal fun `harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har bidiganose med diagnosekode U071 som hoveddiagnose`() {

        coEvery { database.getSykmeldingerMedId(any()) } returns
            getSykmeldingerDBmodelEgenmeldt(
                bidiagnoser = listOf(Diagnose("system", "U071", "tekst")),
                perioder =
                    listOf(
                        getPeriode(
                            fom = LocalDate.of(2020, 3, 10),
                            tom = LocalDate.of(2020, 3, 20),
                        ),
                    ),
            )
        runBlocking {
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldBeEqualTo null
            sykmelding.harRedusertArbeidsgiverperiode shouldBeEqualTo true
        }
    }

    @Test
    internal fun `harRedusertArbeidsgiverperiode skal være true hvis sykmeldingen har bidiganose med diagnosekode U072 som hoveddiagnose`() {
        coEvery { database.getSykmeldingerMedId(any()) } returns
            getSykmeldingerDBmodelEgenmeldt(
                bidiagnoser = listOf(Diagnose("system", "U072", "tekst")),
                perioder =
                    listOf(
                        getPeriode(
                            fom = LocalDate.of(2020, 3, 10),
                            tom = LocalDate.of(2020, 3, 20),
                        ),
                    ),
            )
        runBlocking {
            val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
            sykmelding shouldNotBe null
            sykmelding!!.medisinskVurdering shouldBeEqualTo null
            sykmelding.harRedusertArbeidsgiverperiode shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger som er er innenfor FOM og TOM`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2020, 3, 1),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare fom som er før sykmeldingsperioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 9),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare fom som er midt i perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 16),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare fom som er i slutten av perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 20),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal ikke hente sykmeldinger med bare fom som er i etter perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 21),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Skal ikke hente sykmeldinger med bare fom som er innenfor en av periodene`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 21),
                                tom = LocalDate.of(2020, 2, 21),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 21),
                    null,
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare TOM som er etter perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 21),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare TOM som er i slutten av perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 20),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare TOM som er i midten av perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 15),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare TOM som er i starten av perioden`() {

        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 10),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal ikke hente sykmeldinger med bare TOM som er før perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 9),
                )
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med bare TOM som er innenfor en av periodene`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 5),
                                tom = LocalDate.of(2020, 2, 9),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    null,
                    LocalDate.of(2020, 2, 9),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med FOM og TOM inni en periode`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 11),
                    LocalDate.of(2020, 2, 19),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med FOM og TOM, der fom er før perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2019, 2, 11),
                    LocalDate.of(2020, 2, 19),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med FOM og TOM, der fom er etter perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2019, 2, 20),
                    LocalDate.of(2020, 2, 28),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Skal ikke hente sykmeldinger med FOM og TOM, der de er før perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2019, 2, 5),
                    LocalDate.of(2020, 2, 9),
                )
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Skal ikke hente sykmeldinger med FOM og TOM, der de er etter perioden`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 21),
                    LocalDate.of(2020, 2, 28),
                )
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Skal hente sykmeldinger med FOM og TOM, der de passer minst en periode`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 21),
                                tom = LocalDate.of(2020, 2, 21),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldinger =
                sykmeldingerService.getUserSykmelding(
                    sykmeldingId,
                    LocalDate.of(2020, 2, 21),
                    LocalDate.of(2020, 2, 28),
                )
            sykmeldinger.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få sykmeldt = true hvis sykmeldt på gitt dato (fom)`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                                gradert = Gradert(false, 50),
                            ),
                        ),
                ),
            )

        runBlocking {
            val sykmeldtStatus =
                sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 10))

            sykmeldtStatus shouldBeEqualTo
                SykmeldtStatus(
                    erSykmeldt = true,
                    gradert = true,
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20),
                )
        }
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få sykmeldt = true hvis sykmeldt på gitt dato (tom)`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2019, 2, 10),
                                tom = LocalDate.of(2019, 2, 20),
                                gradert = Gradert(false, 50),
                            ),
                        ),
                ),
            )

        runBlocking {
            val sykmeldtStatus =
                sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 20))

            sykmeldtStatus shouldBeEqualTo
                SykmeldtStatus(
                    erSykmeldt = true,
                    gradert = false,
                    fom = LocalDate.of(2020, 2, 10),
                    tom = LocalDate.of(2020, 2, 20),
                )
        }
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få sykmeldt = false hvis ikke sykmeldt på gitt dato (fom)`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )
        runBlocking {
            val sykmeldtStatus =
                sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 9))

            sykmeldtStatus shouldBeEqualTo
                SykmeldtStatus(erSykmeldt = false, gradert = null, fom = null, tom = null)
        }
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få sykmeldt = false hvis ikke sykmeldt på gitt dato (tom)`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 10),
                                tom = LocalDate.of(2020, 2, 20),
                            ),
                        ),
                ),
            )

        runBlocking {
            val sykmeldtStatus =
                sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 21))

            sykmeldtStatus shouldBeEqualTo
                SykmeldtStatus(erSykmeldt = false, gradert = null, fom = null, tom = null)
        }
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få inneholderGradertPeriode = true hvis inneholder en periode med gradering`() {
        val perioder = mutableListOf<SykmeldingsperiodeDTO>()
        perioder.addAll(getPerioder())
        perioder.addAll(getGradertePerioder())

        val inneholderGradertPeriode = sykmeldingerService.inneholderGradertPeriode(perioder)

        inneholderGradertPeriode shouldBeEqualTo true
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få inneholderGradertPeriode = false hvis ingen perioder er gradert`() {
        val perioder = mutableListOf<SykmeldingsperiodeDTO>()
        perioder.addAll(getPerioder())

        val inneholderGradertPeriode = sykmeldingerService.inneholderGradertPeriode(perioder)

        inneholderGradertPeriode shouldBeEqualTo false
    }

    @Test
    internal fun `Test av sykmeldtStatus Skal få tidligste fom og seneste tom hvis sykmelding har flere perioder`() {
        coEvery { database.getSykmeldinger(any()) } returns
            listOf(
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 8),
                                tom = LocalDate.of(2020, 2, 15),
                            ),
                            getPeriode(
                                fom = LocalDate.of(2020, 2, 16),
                                tom = LocalDate.of(2020, 2, 25),
                                gradert = Gradert(false, 50),
                            ),
                        ),
                ),
                getSykmeldingerDBmodel(
                    perioder =
                        listOf(
                            getPeriode(
                                fom = LocalDate.of(2019, 2, 10),
                                tom = LocalDate.of(2019, 2, 20),
                                gradert = Gradert(false, 50),
                            ),
                        ),
                ),
            )

        runBlocking {
            val sykmeldtStatus =
                sykmeldingerService.getSykmeldtStatusForDato("fnr", LocalDate.of(2020, 2, 20))

            sykmeldtStatus shouldBeEqualTo
                SykmeldtStatus(
                    erSykmeldt = true,
                    gradert = true,
                    fom = LocalDate.of(2020, 2, 8),
                    tom = LocalDate.of(2020, 2, 25),
                )
        }
    }
}
