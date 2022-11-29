package no.nav.syfo.sykmelding.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
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

class MottattSykmeldingServiceTest : FunSpec({
    val database = TestDB.database

    val environment = mockkClass(Environment::class)
    mockEnvironment(environment)
    val applicationState = ApplicationState(true, true)

    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val sykmeldingStatusKafkaProducer = mockk<SykmeldingStatusKafkaProducer>(relaxed = true)
    val mottattSykmeldingStatusService = mockk<MottattSykmeldingStatusService>(relaxed = true)
    val mottattSykmeldingService = MottattSykmeldingService(
        env = environment,
        database = database,
        mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
        sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
        mottattSykmeldingStatusService = mottattSykmeldingStatusService
    )
    val loggingMeta = LoggingMeta(
        sykmeldingId = "123",
        msgId = "123",
        mottakId = "123",
        orgNr = "123"
    )
    afterTest {
        clearAllMocks()
        database.connection.dropData()
    }

    beforeTest {
        applicationState.ready = true
        mockEnvironment(environment)
    }

    afterSpec {
        TestDB.stop()
    }

    context("Test receive sykmelding") {
        test("Handle resending to automatic topic") {
            val receivedSykmelding = getReceivedSykmelding()

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)
            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            coVerify(exactly = 2) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
            coVerify(exactly = 1) { mottattSykmeldingStatusService.handleStatusEventForResentSykmelding(receivedSykmelding.sykmelding.id, receivedSykmelding.personNrPasient) }
        }

        test("should receive sykmelding from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        test("should receive sykmelding med merknad from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding =
                getReceivedSykmelding(merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent")))

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            val merknader = database.connection.getMerknaderForId("1")
            merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
            merknader[0].beskrivelse shouldBeEqualTo "ikke godkjent"
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }

        test("should receive sykmelding from manuell topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.manuellSykmeldingTopic)

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        test("should receive sykmelding from avvist topic and not publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.avvistSykmeldingTopic)

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
            coVerify(exactly = 0) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(any()) }
        }
        test("mottar utenlandsk sykmelding fra ok-topic publiserer på riktig format til mottatt-sykmelding-topic") {
            val receivedSykmelding =
                getReceivedSykmelding(utenlandskSykmelding = UtenlandskSykmelding("SWE", false))

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            val lagretSykmelding = database.connection.getSykmeldingsopplysninger("1")
            lagretSykmelding?.utenlandskSykmelding?.land shouldBeEqualTo "SWE"
            lagretSykmelding?.utenlandskSykmelding?.andreRelevanteOpplysninger shouldBeEqualTo false
            coVerify(exactly = 1) { mottattSykmeldingKafkaProducer.sendMottattSykmelding(match { it.sykmelding.behandler == null && it.sykmelding.utenlandskSykmelding?.land == "SWE" }) }
        }
    }
})

private fun mockEnvironment(environment: Environment) {
    every { environment.applicationName } returns "${MottattSykmeldingServiceTest::class.simpleName}-application"
    every { environment.mottattSykmeldingKafkaTopic } returns "${environment.applicationName}-mottatttopic"
    every { environment.sykmeldingStatusAivenTopic } returns "${environment.applicationName}-statustopic"
    every { environment.okSykmeldingTopic } returns "${environment.applicationName}-oksykmeldingtopic"
    every { environment.behandlingsUtfallTopic } returns "${environment.applicationName}-behandlingsutfallAiven"
    every { environment.avvistSykmeldingTopic } returns "${environment.applicationName}-avvisttopiclAiven"
    every { environment.manuellSykmeldingTopic } returns "${environment.applicationName}-manuelltopic"
    every { environment.cluster } returns "localhost"
}
