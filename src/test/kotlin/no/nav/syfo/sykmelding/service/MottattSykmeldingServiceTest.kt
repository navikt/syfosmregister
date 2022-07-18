package no.nav.syfo.sykmelding.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.Environment
import no.nav.syfo.LoggingMeta
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.Merknad
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.TestDB.Companion.database
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getMerknaderForId
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo

class MottattSykmeldingServiceTest : FunSpec({
    val testDb = TestDB()
    val environment = mockkClass(Environment::class)
    mockEnvironment(environment)
    val applicationState = ApplicationState(true, true)
    val mottattSykmeldingService = UpdateSykmeldingService(
        database = testDb,
        mockk<SykmeldingStatusKafkaProducer>(relaxed = true),
    )
    val loggingMeta = LoggingMeta(
        sykmeldingId = "123",
        msgId = "123",
        mottakId = "123",
        orgNr = "123"
    )
    afterTest {
        clearAllMocks()
        testDb.connection.dropData()
    }

    beforeTest {
        applicationState.ready = true
        mockEnvironment(environment)
    }

    afterSpec {
        testDb.stop()
    }

    context("Test receive sykmelding") {
        test("should receive sykmelding from automatic topic") {
            val receivedSykmelding = getReceivedSykmelding()

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            val lagretSykmelding = testDb.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
        }

        test("should receive sykmelding med merknad from automatic topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding =
                getReceivedSykmelding(merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent")))

            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.okSykmeldingTopic)

            val merknader = testDb.connection.getMerknaderForId("1")
            merknader!![0].type shouldBeEqualTo "UGYLDIG_TILBAKEDATERING"
            merknader[0].beskrivelse shouldBeEqualTo "ikke godkjent"
        }

        test("should receive sykmelding from manuell topic and publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.manuellSykmeldingTopic)

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
        }
        test("should receive sykmelding from avvist topic and not publish to mottatt sykmelding topic") {
            val receivedSykmelding = getReceivedSykmelding()
            mottattSykmeldingService.handleMessageSykmelding(receivedSykmelding, loggingMeta, environment.avvistSykmeldingTopic)

            val lagretSykmelding = database.connection.erSykmeldingsopplysningerLagret("1")
            lagretSykmelding shouldBe true
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
