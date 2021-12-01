package no.nav.syfo.sykmelding.kafka.service

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.db.DatabaseInterface
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
import no.nav.syfo.sykmelding.kafka.model.EnkelSykmelding
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith

object MottattSykmeldingStatusServiceTest : Spek({
    val sykmeldingStatusService = mockk<SykmeldingStatusService>(relaxed = true)
    val sendtSykmeldingKafkaProducer = mockk<SendtSykmeldingKafkaProducer>(relaxed = true)
    val bekreftetSykmeldingKafkaProducer = mockk<BekreftSykmeldingKafkaProducer>(relaxed = true)
    val tombstoneProducer = mockk<SykmeldingTombstoneProducer>()
    val databaseInterface = mockk<DatabaseInterface>(relaxed = true)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftetSykmeldingKafkaProducer, tombstoneProducer, databaseInterface)

    beforeEachTest {
        clearAllMocks()
        every { sykmeldingStatusService.getEnkelSykmelding(any()) } returns opprettEnkelSykmelding()
    }

    describe("Test status event for resendt sykmelding") {
        it("Test resending to sendt topic") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, OffsetDateTime.now(), StatusEvent.SENDT)
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
        it("Test resending to bekreft topic") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, OffsetDateTime.now(), StatusEvent.BEKREFTET)
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
        it("Skal ikke resende når status ikke er sendt/bekreftet") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(sykmeldingId, OffsetDateTime.now(), StatusEvent.APEN)
            )
            every { databaseInterface.getArbeidsgiverStatus(any()) } returns null
            every { databaseInterface.hentSporsmalOgSvar(any()) } returns emptyList()

            mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(sykmeldingId, "123")

            verify(exactly = 0) { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
    }

    describe("Skal ikke oppdatere database hvis skriv til kafka feiler") {

        it("SENDT") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), StatusEvent.APEN
                )
            )
            every { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettSendtStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.registrerSendt(any(), any()) }
        }
        it("BEKREFTET") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), StatusEvent.APEN
                )
            )
            every { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.registrerBekreftet(any(), any()) }
        }
        it("SLETTET") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), StatusEvent.APEN
                )
            )
            every { tombstoneProducer.tombstoneSykmelding(any()) } throws RuntimeException("Noe gikk galt")

            assertFailsWith<RuntimeException> {
                mottattSykmeldingStatusService.handleStatusEvent(opprettSlettetStatusmelding())
            }
            verify(exactly = 0) { sykmeldingStatusService.slettSykmelding(any()) }
        }
    }

    describe("Test av bekreft") {
        it("Bekreft oppdaterer kafka og database") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), StatusEvent.APEN
                )
            )

            mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())

            verify { sykmeldingStatusService.registrerBekreftet(any(), any()) }
            verify { bekreftetSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        it("Bekreft avvist sykmelding oppdaterer kun database") {
            every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, OffsetDateTime.now(ZoneOffset.UTC).minusHours(5), StatusEvent.APEN
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
            OffsetDateTime.now(ZoneOffset.UTC),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "SENDT",
            ArbeidsgiverStatusDTO("9999", null, "Arbeidsplassen AS"),
            listOf(SporsmalOgSvarDTO("tekst", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "svar"))
        )
    )

private fun opprettBekreftStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "BEKREFTET",
            null,
            emptyList()
        )
    )

private fun opprettBekreftStatusmeldingAvvistSykmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "BEKREFTET",
            null,
            null
        )
    )

private fun opprettSlettetStatusmelding() =
    SykmeldingStatusKafkaMessageDTO(
        KafkaMetadataDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "fnr",
            "user"
        ),
        SykmeldingStatusKafkaEventDTO(
            sykmeldingId,
            OffsetDateTime.now(ZoneOffset.UTC),
            "SLETTET",
            null,
            emptyList()
        )
    )

private fun opprettEnkelSykmelding(): EnkelSykmelding =
    EnkelSykmelding(
        id = sykmeldingId,
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
        legekontorOrgnummer = null,
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
        meldingTilArbeidsgiver = null,
        navnFastlege = null,
        tiltakArbeidsplassen = null,
        syketilfelleStartDato = null,
        behandler = BehandlerDTO("fornavn", null, "etternavn", "aktorid", "fnrlege", null, null, AdresseDTO(null, null, null, null, null), null),
        sykmeldingsperioder = emptyList(),
        arbeidsgiver = ArbeidsgiverDTO(null, null),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        prognose = null,
        egenmeldt = false,
        papirsykmelding = false,
        harRedusertArbeidsgiverperiode = false,
        merknader = null
    )
