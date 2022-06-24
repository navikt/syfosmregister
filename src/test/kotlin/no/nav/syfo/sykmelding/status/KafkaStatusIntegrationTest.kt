package no.nav.syfo.sykmelding.status

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.createListener
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_SLETTET
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.api.model.SykmeldingStatusEventDTO
import no.nav.syfo.sykmelding.status.api.registerSykmeldingStatusGETApi
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import no.nav.syfo.testutil.KafkaTest
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import java.time.ZoneOffset

@DelicateCoroutinesApi
class KafkaStatusIntegrationTest : FunSpec({

    val database = TestDB()

    val environment = mockkClass(Environment::class)
    setUpEnvironment(environment)

    val sykmelding = testSykmeldingsopplysninger
    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
    val applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusService = spyk(SykmeldingStatusService(database))
    val consumer = KafkaFactory.getKafkaStatusConsumerAiven(kafkaConfig, environment)
    val sendtSykmeldingKafkaProducer = spyk(KafkaFactory.getSendtSykmeldingKafkaProducer(kafkaConfig, environment))
    val bekreftSykmeldingKafkaProducer = spyk(KafkaFactory.getBekreftetSykmeldingKafkaProducer(kafkaConfig, environment))
    val tombstoneProducer = spyk(KafkaFactory.getTombstoneProducer(kafkaConfig, environment))
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, tombstoneProducer, database)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(consumer, applicationState, mottattSykmeldingStatusService)
    val sykmeldingerService = SykmeldingerService(database)
    val mockPayload = mockk<Payload>()

    afterSpec {
        applicationState.ready = false
        applicationState.alive = false
        database.stop()
    }

    beforeTest {
        applicationState.alive = true
        applicationState.ready = true
        clearAllMocks()
        setUpEnvironment(environment)
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
        database.connection.dropData()
        database.lagreMottattSykmelding(sykmelding, testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }

    context("Read from status topic and save in DB") {
        test("Write and read APEN status") {
            coEvery { sykmeldingStatusService.registrerStatus(any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

            kafkaProducer.send(
                getApenEvent(sykmelding),
                testSykmeldingsopplysninger.pasientFnr
            )

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 1
            sykmeldinger[0].status shouldBeEqualTo StatusDbModel(
                statusTimestamp = sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                statusEvent = "APEN",
                arbeidsgiver = null
            )
            database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 1
        }

        test("test tombstone") {
            bekreftSykmeldingKafkaProducer.tombstoneSykmelding("123")
        }

        test("write and read APEN and SENDT") {
            coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

            kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
            val sendEvent = getSendtEvent(sykmelding)
            kafkaProducer.send(sendEvent, sykmelding.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 1
            val sykmeldingstatus = sykmeldinger[0].status
            sykmeldingstatus shouldBeEqualTo StatusDbModel(
                statusTimestamp = sendEvent.timestamp,
                statusEvent = "SENDT",
                arbeidsgiver = ArbeidsgiverDbModel("org", "jorg", "navn")
            )

            database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 2
        }

        test("Should test APEN and BEKREFTET") {
            coEvery { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

            kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
            val bekreftetEvent = getSykmeldingBekreftEvent(sykmelding)
            kafkaProducer.send(bekreftetEvent, sykmelding.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 1
            val sykmeldingStatus = sykmeldinger[0].status
            sykmeldingStatus shouldBeEqualTo StatusDbModel(
                statusTimestamp = bekreftetEvent.timestamp,
                statusEvent = "BEKREFTET",
                arbeidsgiver = null
            )

            database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 2
        }

        test("Should test APEN and SLETTET") {
            coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }
            kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
            kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
            coVerify(exactly = 1) { tombstoneProducer.tombstoneSykmelding(any()) }
        }
        test("should test APEN -> SENDT -> SLETTET") {
            coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }
            kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
            kafkaProducer.send(getSendtEvent(sykmelding), sykmelding.pasientFnr)
            kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
            database.finnSvarForSykmelding(sykmelding.id).size shouldBeEqualTo 0

            coVerify(exactly = 1) { tombstoneProducer.tombstoneSykmelding(any()) }
            coVerify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
            coVerify(exactly = 1) { sendtSykmeldingKafkaProducer.tombstoneSykmelding(any()) }
            coVerify(exactly = 0) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
            coVerify(exactly = 0) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(any()) }
        }

        test("should test APEN -> BEKREFTET -> SLETTET") {
            coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }
            kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
            kafkaProducer.send(getSykmeldingBekreftEvent(sykmelding), sykmelding.pasientFnr)
            kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
            coVerify(exactly = 1) { tombstoneProducer.tombstoneSykmelding(any()) }
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.tombstoneSykmelding(any()) }
            coVerify(exactly = 1) { bekreftSykmeldingKafkaProducer.sendSykmelding(any()) }
            coVerify(exactly = 1) { bekreftSykmeldingKafkaProducer.tombstoneSykmelding(any()) }
        }
    }

    context("Test Kafka -> DB -> status API") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing {
                registerSykmeldingStatusGETApi(sykmeldingStatusService)
                route("/api/v2") {
                    registrerSykmeldingApiV2(sykmeldingerService)
                }
            }
            test("Test get stykmeldingstatus latest should be SENDT") {
                val sendtEvent = publishSendAndWait(sykmeldingStatusService, applicationState, kafkaProducer, sykmelding, sykmeldingStatusConsumerService)
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/uuid/status?filter=LATEST") {
                        call.authentication.principal = BrukerPrincipal("pasientFnr", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldingStatuser = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    sykmeldingStatuser.size shouldBeEqualTo 1
                    val latestSykmeldingStatus = sykmeldingStatuser[0]
                    latestSykmeldingStatus shouldBeEqualTo SykmeldingStatusEventDTO(
                        StatusEventDTO.SENDT,
                        sendtEvent.timestamp,
                        erAvvist = false,
                        erEgenmeldt = false
                    )
                }
            }
            test("test get sykmelding with latest status SENDT") {
                val sendtEvent = publishSendAndWait(sykmeldingStatusService, applicationState, kafkaProducer, sykmelding, sykmeldingStatusConsumerService)
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        call.authentication.principal = BrukerPrincipal("pasientFnr", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldingDTO = objectMapper.readValue<List<SykmeldingDTO>>(response.content!!)[0]
                    val latestSykmeldingStatus = sykmeldingDTO.sykmeldingStatus
                    latestSykmeldingStatus shouldBeEqualTo no.nav.syfo.sykmelding.model.SykmeldingStatusDTO(
                        timestamp = sendtEvent.timestamp,
                        sporsmalOgSvarListe = listOf(
                            SporsmalDTO(
                                tekst = "din arbeidssituasjon?",
                                svar = SvarDTO(
                                    no.nav.syfo.sykmelding.model.SvartypeDTO.ARBEIDSSITUASJON,
                                    "ARBEIDSTAKER"
                                ),
                                shortName = no.nav.syfo.sykmelding.model.ShortNameDTO.ARBEIDSSITUASJON
                            )
                        ),
                        arbeidsgiver = no.nav.syfo.sykmelding.status.api.ArbeidsgiverStatusDTO("org", "jorg", "navn"),
                        statusEvent = "SENDT"

                    )
                }
            }
        }
    }
})

fun getSlettetEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmelding.id, getNowTickMillisOffsetDateTime(), STATUS_SLETTET, null, null)
}

private fun setUpEnvironment(environment: Environment) {
    every { environment.applicationName } returns "KafkaStatusIntegrationTest-application"
    every { environment.sykmeldingStatusAivenTopic } returns "KafkaStatusIntegrationTest-topic"
    every { environment.sendSykmeldingKafkaTopic } returns "KafkaStatusIntegrationTest-sendt-sykmelding-topic"
    every { environment.bekreftSykmeldingKafkaTopic } returns "KafkaStatusIntegrationTest-syfo-bekreftet-sykmelding"
    every { environment.mottattSykmeldingKafkaTopic } returns "KafkaStatusIntegrationTest-syfo-mottatt-sykmelding"
    every { environment.cluster } returns "localhost"
    every { environment.okSykmeldingTopic } returns "KafkaStatusIntegrationTestoksykmeldingtopic"
    every { environment.behandlingsUtfallTopic } returns "KafkaStatusIntegrationTestbehandlingsutfallAiven"
    every { environment.avvistSykmeldingTopic } returns "KafkaStatusIntegrationTestavvisttopiclAiven"
    every { environment.manuellSykmeldingTopic } returns "KafkaStatusIntegrationTestmanuelltopic"
    every { environment.mottattSykmeldingKafkaTopic } returns "KafkaStatusIntegrationTestmanuelltopic"
}

@DelicateCoroutinesApi
private fun publishSendAndWait(sykmeldingStatusService: SykmeldingStatusService, applicationState: ApplicationState, kafkaProducer: SykmeldingStatusKafkaProducer, sykmelding: Sykmeldingsopplysninger, sykmeldingStatusConsumerService: SykmeldingStatusConsumerService): SykmeldingStatusKafkaEventDTO {
    coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
        callOriginal()
        applicationState.alive = false
        applicationState.ready = false
    }
    kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
    val sendtEvent = getSendtEvent(sykmelding)
    kafkaProducer.send(sendtEvent, sykmelding.pasientFnr)
    val job = createListener(applicationState) {
        sykmeldingStatusConsumerService.start()
    }
    runBlocking {
        job.join()
    }
    return sendtEvent
}

private fun getSykmeldingBekreftEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        statusEvent = STATUS_BEKREFTET,
        timestamp = sykmelding.mottattTidspunkt.plusHours(1).atOffset(ZoneOffset.UTC),
        arbeidsgiver = null,
        sporsmals = listOf(SporsmalOgSvarDTO("sporsmal", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "NEI"))
    )
}

private fun getSendtEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        timestamp = sykmelding.mottattTidspunkt.plusHours(1).atOffset(ZoneOffset.UTC),
        arbeidsgiver = ArbeidsgiverStatusDTO("org", "jorg", "navn"),
        statusEvent = STATUS_SENDT,
        sporsmals = listOf(
            SporsmalOgSvarDTO("din arbeidssituasjon?", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER")
        )
    )
}

private fun getApenEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        timestamp = sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC),
        statusEvent = STATUS_APEN
    )
}
