package no.nav.syfo.sykmelding.kafka.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import java.util.UUID
import kotlin.test.assertFailsWith
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.getArbeidsgiverStatus
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime

class MottattSykmeldingStatusServiceTest :
    FunSpec({
        val sykmeldingStatusService = mockk<SykmeldingStatusService>(relaxed = true)
        val sendtSykmeldingKafkaProducer = mockk<SendtSykmeldingKafkaProducer>(relaxed = true)
        val bekreftetSykmeldingKafkaProducer = mockk<BekreftSykmeldingKafkaProducer>(relaxed = true)
        val tombstoneProducer = mockk<SykmeldingTombstoneProducer>()
        val databaseInterface = mockkClass(DatabaseInterface::class)
        val mottattSykmeldingStatusService =
            MottattSykmeldingStatusService(
                sykmeldingStatusService,
                sendtSykmeldingKafkaProducer,
                bekreftetSykmeldingKafkaProducer,
                tombstoneProducer,
                databaseInterface,
            )

        beforeTest {
            clearAllMocks()
            mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingQueriesKt")
            coEvery { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns
                opprettArbeidsgiverSykmelding()
        }

        context("Test status event for resendt sykmelding") {
            test("Test resending to sendt topic") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.SENDT,
                    )
                coEvery { databaseInterface.hentSporsmalOgSvar(any()) } returns
                    listOf(
                        Sporsmal(
                            "ARBEIDSGIVER",
                            ShortName.ARBEIDSSITUASJON,
                            Svar(
                                sykmeldingId,
                                null,
                                Svartype.ARBEIDSSITUASJON,
                                "ARBEIDSTAKER",
                            ),
                        ),
                    )
                coEvery { databaseInterface.getArbeidsgiverStatus(any()) } returns
                    ArbeidsgiverDbModel(
                        "orgnummer",
                        "juridisk",
                        "orgnavn",
                    )

                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    sykmeldingId,
                    "123"
                )

                coVerify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
                coVerify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
            }
            test("Test resending to bekreft topic") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.BEKREFTET,
                    )

                coEvery { databaseInterface.getArbeidsgiverStatus(any()) } returns
                    ArbeidsgiverDbModel(
                        "orgnummer",
                        "juridisk",
                        "orgnavn",
                    )
                coEvery { databaseInterface.hentSporsmalOgSvar(any()) } returns
                    listOf(
                        Sporsmal(
                            "ARBEIDSGIVER",
                            ShortName.ARBEIDSSITUASJON,
                            Svar(
                                sykmeldingId,
                                null,
                                Svartype.ARBEIDSSITUASJON,
                                "ARBEIDSTAKER",
                            ),
                        ),
                    )

                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    sykmeldingId,
                    "123"
                )

                coVerify(exactly = 1) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
                coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
            }
            test("Skal ikke resende når status ikke er sendt/bekreftet") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.APEN,
                    )
                coEvery { databaseInterface.getArbeidsgiverStatus(any()) } returns null
                coEvery { databaseInterface.hentSporsmalOgSvar(any()) } returns emptyList()

                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    sykmeldingId,
                    "123"
                )

                coVerify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
                coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
            }
        }

        context("Skal ikke oppdatere database hvis skriv til kafka feiler") {
            test("SENDT") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.APEN,
                    )
                coEvery { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } throws
                    RuntimeException("Noe gikk galt")

                assertFailsWith<RuntimeException> {
                    mottattSykmeldingStatusService.handleStatusEvent(opprettSendtStatusmelding())
                }
                coVerify(exactly = 0) { sykmeldingStatusService.registrerSendt(any(), any()) }
            }
            test("BEKREFTET") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.APEN,
                    )
                coEvery { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) } throws
                    RuntimeException("Noe gikk galt")

                assertFailsWith<RuntimeException> {
                    mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())
                }
                coVerify(exactly = 0) {
                    sykmeldingStatusService.registrerBekreftet(any(), any(), any())
                }
            }
            test("SLETTET") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.APEN,
                    )
                coEvery { tombstoneProducer.tombstoneSykmelding(any()) } throws
                    RuntimeException("Noe gikk galt")

                assertFailsWith<RuntimeException> {
                    mottattSykmeldingStatusService.handleStatusEvent(opprettSlettetStatusmelding())
                }
                coVerify(exactly = 0) { sykmeldingStatusService.slettSykmelding(any()) }
            }
        }

        context("Test av bekreft") {
            test("Bekreft oppdaterer kafka og database") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.APEN,
                    )

                mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())

                coVerify { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) }
                coVerify { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            }
            test("Bekreft avvist sykmelding oppdaterer kun database") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.APEN,
                    )

                mottattSykmeldingStatusService.handleStatusEvent(
                    opprettBekreftStatusmeldingAvvistSykmelding()
                )

                coVerify { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) }
                coVerify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            }
        }

        context("Test av oppdatering av egenmeldingsdager (aka SENDT->SENDT)") {
            test(
                "Skal tillate at en SENDT sykmelding blir SENDT på nytt med nye egenmeldingsdager"
            ) {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.SENDT,
                    )

                mottattSykmeldingStatusService.handleStatusEvent(
                    opprettSendtStatusmelding(erSvarOppdatering = true)
                )

                coVerify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
                coVerify(exactly = 1) { sykmeldingStatusService.registrerSendt(any(), any()) }
            }

            test(
                "Skal IKKE tillate at en SENDT sykmelding blir SENDT på nytt når den ikke har erSvarOppdatering=true"
            ) {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime().minusHours(5),
                        StatusEvent.SENDT,
                    )

                mottattSykmeldingStatusService.handleStatusEvent(
                    opprettSendtStatusmelding(erSvarOppdatering = false)
                )

                coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
                coVerify(exactly = 0) { sykmeldingStatusService.registrerSendt(any(), any()) }
            }
        }

        context("Tester resending av tidligerearbeidsgiver sykmeldingStatusKafkaMessage") {
            test("sender med tidligere arbeidsgiver på sykmelding som allerede er bekreftet") {
                coEvery { sykmeldingStatusService.getLatestSykmeldingStatus(any()) } returns
                    SykmeldingStatusEvent(
                        sykmeldingId,
                        getNowTickMillisOffsetDateTime(),
                        StatusEvent.BEKREFTET,
                    )

                val tidligereArbeidsgiverDto =
                    TidligereArbeidsgiverKafkaDTO("orgnamn", "orgnummer", sykmeldingId)
                coEvery { sykmeldingStatusService.getTidligereArbeidsgiver(any()) } returns
                    TidligereArbeidsgiverDTO("orgnamn", "orgnummer", sykmeldingId)

                coEvery { databaseInterface.hentSporsmalOgSvar(any()) } returns emptyList()

                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    sykmeldingId,
                    "123"
                )

                coVerify {
                    bekreftetSykmeldingKafkaProducer.sendSykmelding(
                        match { it.event.tidligereArbeidsgiver == tidligereArbeidsgiverDto }
                    )
                }
            }
        }
    })

val sykmeldingId = UUID.randomUUID().toString()

private fun opprettSendtStatusmelding(erSvarOppdatering: Boolean = false) =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user",
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "SENDT",
            ArbeidsgiverStatusKafkaDTO("9999", null, "Arbeidsplassen AS"),
            listOf(
                SporsmalOgSvarKafkaDTO(
                    "tekst",
                    ShortNameKafkaDTO.ARBEIDSSITUASJON,
                    SvartypeKafkaDTO.ARBEIDSSITUASJON,
                    "svar"
                )
            ),
            erSvarOppdatering = erSvarOppdatering,
        ),
    )

private fun opprettBekreftStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user",
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "BEKREFTET",
            null,
            emptyList(),
        ),
    )

private fun opprettBekreftStatusmeldingAvvistSykmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user",
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "BEKREFTET",
            null,
            null,
        ),
    )

private fun opprettSlettetStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user",
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "SLETTET",
            null,
            emptyList(),
        ),
    )

private fun opprettArbeidsgiverSykmelding(): ArbeidsgiverSykmelding =
    ArbeidsgiverSykmelding(
        id = sykmeldingId,
        mottattTidspunkt = getNowTickMillisOffsetDateTime().minusDays(1),
        behandletTidspunkt = getNowTickMillisOffsetDateTime().minusDays(1),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        syketilfelleStartDato = null,
        behandler =
            BehandlerAGDTO(
                "fornavn",
                null,
                "etternavn",
                null,
                AdresseDTO(null, null, null, null, null),
                null
            ),
        sykmeldingsperioder = emptyList(),
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        prognose = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = null,
        utenlandskSykmelding = null,
        signaturDato = getNowTickMillisOffsetDateTime(),
    )
