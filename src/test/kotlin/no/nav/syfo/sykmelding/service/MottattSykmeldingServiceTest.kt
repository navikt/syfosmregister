package no.nav.syfo.sykmelding.service

import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getMerknaderForId
import no.nav.syfo.testutil.getSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MottattSykmeldingServiceTest {
    val database = TestDB.database

    val environment = mockkClass(Environment::class)
    val applicationState = ApplicationState(alive = true, ready = true)

    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val sykmeldingStatusKafkaProducer = mockk<SykmeldingStatusKafkaProducer>(relaxed = true)
    val mottattSykmeldingStatusService = mockk<MottattSykmeldingStatusService>(relaxed = true)
    val mottattSykmeldingService =
        MottattSykmeldingService(
            env = environment,
            database = database,
            mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
            sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
            mottattSykmeldingStatusService = mottattSykmeldingStatusService,
        )
    val loggingMeta =
        LoggingMeta(
            sykmeldingId = "123",
            msgId = "123",
            mottakId = "123",
            orgNr = "123",
        )

    @AfterEach
    fun afterTest() {
        clearAllMocks()
        database.connection.dropData()
    }

    @BeforeEach
    fun beforeTest() {
        applicationState.ready = true
        mockEnvironment(environment)
    }

    companion object {
        @AfterAll
        @JvmStatic
        internal fun tearDown() {
            TestDB.stop()
        }
    }

    @Test
    internal fun `Test receive sykmelding Handle resending to automatic topic`() {
        val receivedSykmelding = getReceivedSykmelding()

        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.okSykmeldingTopic,
            )
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.okSykmeldingTopic,
            )

            coVerify(exactly = 2) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
            coVerify(exactly = 1) {
                mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding.personNrPasient,
                )
            }
        }
    }

    @Test
    internal fun `Test receive sykmelding should receive sykmelding from automatic topic and publish to mottatt sykmelding topic`() {

        val receivedSykmelding = getReceivedSykmelding()
        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.okSykmeldingTopic,
            )

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }

    @Test
    internal fun `Test receive sykmelding should receive sykmelding med merknad from automatic topic and publish to mottatt sykmelding topic`() {

        val receivedSykmelding =
            getReceivedSykmelding(
                merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent")),
            )
        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.okSykmeldingTopic,
            )

            val merknader = database.connection.getMerknaderForId("1")
            merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
            merknader[0].beskrivelse shouldBeEqualTo "ikke godkjent"
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }

    @Test
    internal fun `Test receive sykmelding should receive sykmelding from manuell topic and publish to mottatt sykmelding topic`() {
        val receivedSykmelding = getReceivedSykmelding()
        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.manuellSykmeldingTopic,
            )

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }

    @Test
    internal fun `Test receive sykmelding should receive sykmelding from avvist topic and not publish to mottatt sykmelding topic`() {

        val receivedSykmelding = getReceivedSykmelding()
        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.avvistSykmeldingTopic,
            )

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
    }

    @Test
    internal fun `Test receive sykmelding mottar utenlandsk sykmelding fra ok-topic publiserer på riktig format til mottatt-sykmelding-topic`() {
        val receivedSykmelding =
            getReceivedSykmelding(utenlandskSykmelding = UtenlandskSykmelding("SWE", false))
        runBlocking {
            mottattSykmeldingService.handleMessageSykmelding(
                receivedSykmelding,
                loggingMeta,
                environment.okSykmeldingTopic,
            )

            val lagretSykmelding = database.connection.getSykmeldingsopplysninger("1")
            lagretSykmelding?.utenlandskSykmelding?.land shouldBeEqualTo "SWE"
            coVerify(exactly = 1) {
                mottattSykmeldingKafkaProducer.sendMottattSykmelding(
                    match {
                        it.sykmelding.behandler == null &&
                            it.sykmelding.utenlandskSykmelding?.land == "SWE"
                    },
                )
            }
        }
    }
}

private fun mockEnvironment(environment: Environment) {
    every { environment.applicationName } returns
        "${MottattSykmeldingServiceTest::class.simpleName}-application"
    every { environment.mottattSykmeldingKafkaTopic } returns
        "${environment.applicationName}-mottatttopic"
    every { environment.sykmeldingStatusAivenTopic } returns
        "${environment.applicationName}-statustopic"
    every { environment.okSykmeldingTopic } returns
        "${environment.applicationName}-oksykmeldingtopic"
    every { environment.behandlingsUtfallTopic } returns
        "${environment.applicationName}-behandlingsutfallAiven"
    every { environment.avvistSykmeldingTopic } returns
        "${environment.applicationName}-avvisttopiclAiven"
    every { environment.manuellSykmeldingTopic } returns
        "${environment.applicationName}-manuelltopic"
    every { environment.cluster } returns "localhost"
}
