package no.nav.syfo.sykmelding.kafka.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import java.util.UUID

class MottattSykmeldingStatusServiceTest : FunSpec({
    val sykmeldingStatusService = mockk<SykmeldingStatusService>(relaxed = true)
    val mottattSykmeldingStatusService = UpdateStatusService(sykmeldingStatusService)

    beforeTest {
        clearAllMocks()
        mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingQueriesKt")
        coEvery { sykmeldingStatusService.getArbeidsgiverSykmelding(any()) } returns opprettArbeidsgiverSykmelding()
    }

    context("Test av bekreft") {
        test("Bekreft oppdaterer kafka og database") {
            coEvery { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )

            mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmelding())

            coVerify { sykmeldingStatusService.registrerBekreftet(any(), any()) }
        }
        test("Bekreft avvist sykmelding oppdaterer kun database") {
            coEvery { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                SykmeldingStatusEvent(
                    sykmeldingId, getNowTickMillisOffsetDateTime().minusHours(5), StatusEvent.APEN
                )
            )

            mottattSykmeldingStatusService.handleStatusEvent(opprettBekreftStatusmeldingAvvistSykmelding())

            coVerify { sykmeldingStatusService.registrerBekreftet(any(), any()) }
        }
    }
})

val sykmeldingId = UUID.randomUUID().toString()
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
