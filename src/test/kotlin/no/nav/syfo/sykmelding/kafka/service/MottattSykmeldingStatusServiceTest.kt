package no.nav.syfo.sykmelding.kafka.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.getArbeidsgiverStatus
import no.nav.syfo.sykmelding.db.hentSporsmalOgSvar
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
import java.util.UUID
import kotlin.test.assertFailsWith

class MottattSykmeldingStatusServiceTest : FunSpec({
    val sykmeldingStatusService = mockk<SykmeldingStatusService>(relaxed = true)
    val sendtSykmeldingKafkaProducer = mockk<SendtSykmeldingKafkaProducer>(relaxed = true)
    val bekreftetSykmeldingKafkaProducer = mockk<BekreftSykmeldingKafkaProducer>(relaxed = true)
    val tombstoneProducer = mockk<SykmeldingTombstoneProducer>()
    val databaseInterface = mockk<DatabaseInterface>(relaxed = true)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftetSykmeldingKafkaProducer, tombstoneProducer, databaseInterface)

    beforeTest {
        clearAllMocks()
        every { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns opprettArbeidsgiverSykmelding()
    }

    context("Test status event for resendt sykmelding") {
        test("Test resending to sendt topic") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, getNowTickMillisOffsetDateTime(), StatusEvent.SENDT)
            )
            every { databaseInterface.getArbeidsgiverStatus(any()) } returns ArbeidsgiverDbModel("orgnummer", "juridisk", "orgnavn")
            every { databaseInterface.hentSporsmalOgSvar(any()) } returns listOf(
                Sporsmal(
                    "ARBEIDSGIVER", ShortName.ARBEIDSSITUASJON,
                    Svar(
                        sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"
                    )
                )
            )

            mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(sykmeldingId, "123")

            verify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            verify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        test("Test resending to bekreft topic") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, getNowTickMillisOffsetDateTime(), StatusEvent.BEKREFTET)
            )
            every { databaseInterface.getArbeidsgiverStatus(any()) } returns ArbeidsgiverDbModel("orgnummer", "juridisk", "orgnavn")
            every { databaseInterface.hentSporsmalOgSvar(any()) } returns listOf(
                Sporsmal(
                    "ARBEIDSGIVER", ShortName.ARBEIDSSITUASJON,
                    Svar(
                        sykmeldingId, null, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"
                    )
                )
            )

            mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(sykmeldingId, "123")

            verify(exactly = 1) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        test("Skal ikke resende når status ikke er sendt/bekreftet") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, getNowTickMillisOffsetDateTime(), StatusEvent.APEN)
            )
            every { databaseInterface.getArbeidsgiverStatus(any()) } returns null
            every { databaseInterface.hentSporsmalOgSvar(any()) } returns emptyList()

            mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(sykmeldingId, "123")

            verify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
    }

    context("Skal ikke oppdatere database hvis skriv til kafka feiler") {

        test("SENDT") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )
            every { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettSendtStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.registrerSendt(any(), any()) }
        }
        test("BEKREFTET") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )
            every { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.registrerBekreftet(any(), any()) }
        }
        test("SLETTET") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )
            every { tombstoneProducer.tombstoneSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettSlettetStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.slettSykmelding(any()) }
        }
    }

    context("Test av bekreft") {
        test("Bekreft oppdaterer kafka og database") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )

            mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())

            verify { sykmeldingStatusService.registrerBekreftet(any(), any()) }
            verify { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        test("Bekreft avvist sykmelding oppdaterer kun database") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )

            mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmeldingAvvistSykmelding())

            verify { sykmeldingStatusService.registrerBekreftet(any(), any()) }
            verify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
    }
})

val sykmeldingId = UUID.randomUUID().toString()

private fun opprettSendtStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "SENDT",
            ArbeidsgiverStatusDTO("9999", null, "Arbeidsplassen AS"),
            listOf(SporsmalOgSvarDTO("tekst", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "svar"))
        )
    )

private fun opprettBekreftStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "BEKREFTET",
            null,
            emptyList()
        )
    )

private fun opprettBekreftStatusmeldingAvvistSykmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "BEKREFTET",
            null,
            null
        )
    )

private fun opprettSlettetStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            getNowTickMillisOffsetDateTime(),
            "SLETTET",
            null,
            emptyList()
        )
    )

private fun opprettArbeidsgiverSykmelding(): ArbeidsgiverSykmelding =
    ArbeidsgiverSykmelding(
        id = sykmeldingId,
        mottattTidspunkt = getNowTickMillisOffsetDateTime().minusDays(1),
        behandletTidspunkt = getNowTickMillisOffsetDateTime().minusDays(1),
        meldingTilArbeidsgiver = null,
        tiltakArbeidsplassen = null,
        syketilfelleStartDato = null,
        behandler = BehandlerAGDTO("fornavn", null, "etternavn", null, AdresseDTO(null, null, null, null, null), null),
        sykmeldingsperioder = emptyList(),
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        prognose = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = null
    )
