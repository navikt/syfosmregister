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
import io.mockk.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.createListener
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.db.ArbeidsgiverDbModel
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.STATUS_APEN
import no.nav.syfo.sykmelding.kafka.model.STATUS_AVBRUTT
import no.nav.syfo.sykmelding.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmelding.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmelding.kafka.model.STATUS_SLETTET
import no.nav.syfo.sykmelding.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmelding.model.SporsmalDTO
import no.nav.syfo.sykmelding.model.SvarDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo

@DelicateCoroutinesApi
class KafkaStatusIntegrationTest :
    FunSpec({
        val database = TestDB.database

        val environment = mockkClass(Environment::class)
        setUpEnvironment(environment)

        val sykmelding = testSykmeldingsopplysninger
        val kafkaConfig = KafkaTest.setupKafkaConfig()
        val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(environment, kafkaConfig)
        val applicationState = ApplicationState(alive = true, ready = true)
        val sykmeldingStatusService = spyk(SykmeldingStatusService(database))
        val consumer = KafkaFactory.getKafkaStatusConsumerAiven(environment, kafkaConfig)
        val sendtSykmeldingKafkaProducer =
            spyk(KafkaFactory.getSendtSykmeldingKafkaProducer(environment, kafkaConfig))
        val bekreftSykmeldingKafkaProducer =
            spyk(KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment, kafkaConfig))
        val tombstoneProducer = spyk(KafkaFactory.getTombstoneProducer(environment, kafkaConfig))
        val mottattSykmeldingStatusService =
            MottattSykmeldingStatusService(
                sykmeldingStatusService,
                sendtSykmeldingKafkaProducer,
                bekreftSykmeldingKafkaProducer,
                tombstoneProducer,
                database
            )
        val sykmeldingStatusConsumerService =
            SykmeldingStatusConsumerService(
                consumer,
                applicationState,
                mottattSykmeldingStatusService
            )
        val sykmeldingerService = SykmeldingerService(database)
        val mockPayload = mockk<Payload>()

        afterSpec {
            applicationState.ready = false
            applicationState.alive = false
            TestDB.stop()
        }

        afterTest { database.connection.dropData() }

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
                coEvery { sykmeldingStatusService.registrerStatus(any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }

                kafkaProducer.send(
                    getApenEvent(sykmelding),
                    testSykmeldingsopplysninger.pasientFnr,
                )

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 1
                sykmeldinger[0].status shouldBeEqualTo
                    StatusDbModel(
                        statusTimestamp = sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC),
                        statusEvent = "APEN",
                        arbeidsgiver = null,
                    )
                database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 1
            }

            test("write and read APEN and SENDT") {
                coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }

                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                val sendEvent = getSendtEvent(sykmelding)
                kafkaProducer.send(sendEvent, sykmelding.pasientFnr)

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 1
                val sykmeldingstatus = sykmeldinger[0].status
                sykmeldingstatus shouldBeEqualTo
                    StatusDbModel(
                        statusTimestamp = sendEvent.timestamp,
                        statusEvent = "SENDT",
                        arbeidsgiver = ArbeidsgiverDbModel("org", "jorg", "navn"),
                    )

                database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 2
            }

            test("Should test APEN and BEKREFTET") {
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }

                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                val bekreftetEvent = getSykmeldingBekreftEvent(sykmelding)
                kafkaProducer.send(bekreftetEvent, sykmelding.pasientFnr)

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 1
                val sykmeldingStatus = sykmeldinger[0].status
                sykmeldingStatus shouldBeEqualTo
                    StatusDbModel(
                        statusTimestamp = bekreftetEvent.timestamp,
                        statusEvent = "BEKREFTET",
                        arbeidsgiver = null,
                    )

                database.hentSykmeldingStatuser(sykmelding.id).size shouldBeEqualTo 2
            }

            test("Should test APEN and SLETTET") {
                coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 0
            }
            test("should test APEN -> SENDT -> SLETTET") {
                coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                kafkaProducer.send(getSendtEvent(sykmelding), sykmelding.pasientFnr)
                kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 0
                database.finnSvarForSykmelding(sykmelding.id).size shouldBeEqualTo 0
            }

            test("should test APEN -> BEKREFTET -> SLETTET") {
                coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                kafkaProducer.send(getSykmeldingBekreftEvent(sykmelding), sykmelding.pasientFnr)
                kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
                sykmeldinger.size shouldBeEqualTo 0
            }

            test("lagrer tidligereArbeidsgiver i databasen") {
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }

                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                val tidligereArbeidsgiver =
                    TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                kafkaProducer.send(
                    getSykmeldingBekreftEvent(sykmelding, tidligereArbeidsgiver),
                    sykmelding.pasientFnr
                )

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

                val tidligereArbeidsgiverList =
                    database.connection.getTidligereArbeidsgiver(sykmelding.id)

                tidligereArbeidsgiverList.size shouldBeEqualTo 1
            }

            test("sletter tidligere arbeidsgiver fra basen") {
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }

                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                val tidligereArbeidsgiver =
                    TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
                kafkaProducer.send(
                    getSykmeldingBekreftEvent(sykmelding, tidligereArbeidsgiver),
                    sykmelding.pasientFnr
                )

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }
                var tidligereArbeidsgiverList =
                    database.connection.getTidligereArbeidsgiver(sykmelding.id)

                tidligereArbeidsgiverList.size shouldBeEqualTo 1

                kafkaProducer.send(getSendtEvent(sykmelding), sykmelding.pasientFnr)
                applicationState.alive = true
                applicationState.ready = true

                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }
                tidligereArbeidsgiverList =
                    database.connection.getTidligereArbeidsgiver(sykmelding.id)
                tidligereArbeidsgiverList.size shouldBeEqualTo 0
            }

            test("sletter bekreftet etter bekreftet") {
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
                    {
                        callOriginal()
                    } andThenAnswer
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                val apenEvent = getApenEvent(sykmelding)
                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
                val tidligereArbeidsgiver1 = TidligereArbeidsgiverKafkaDTO("ag1", "orgnummer", "1")
                kafkaProducer.send(
                    getSykmeldingBekreftEvent(
                        sykmelding,
                        tidligereArbeidsgiver1,
                        timestamp = apenEvent.timestamp.plusMinutes(1)
                    ),
                    sykmelding.pasientFnr
                )
                val tidligereArbeidsgiver2 = TidligereArbeidsgiverKafkaDTO("ag2", "orgnummer", "1")

                kafkaProducer.send(
                    getSykmeldingBekreftEvent(
                        sykmelding,
                        tidligereArbeidsgiver2,
                        apenEvent.timestamp.plusMinutes(1)
                    ),
                    sykmelding.pasientFnr
                )
                runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }
                val tidligereArbeidsgiverList =
                    database.connection.getTidligereArbeidsgiver(sykmelding.id)

                tidligereArbeidsgiverList.size shouldBeEqualTo 1
                tidligereArbeidsgiverList
                    .singleOrNull()
                    ?.tidligereArbeidsgiver
                    ?.orgNavn shouldBeEqualTo tidligereArbeidsgiver2.orgNavn
                tidligereArbeidsgiverList
                    .singleOrNull()
                    ?.tidligereArbeidsgiver
                    ?.orgnummer shouldBeEqualTo tidligereArbeidsgiver2.orgnummer
                tidligereArbeidsgiverList
                    .singleOrNull()
                    ?.tidligereArbeidsgiver
                    ?.sykmeldingsId shouldBeEqualTo tidligereArbeidsgiver2.sykmeldingsId
            }

            test("sletter avbrutt etter bekreftet") {
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
                    {
                        callOriginal()
                    }
                coEvery {
                    sykmeldingStatusService.registrerStatus(match { it.event == StatusEvent.APEN })
                } answers { callOriginal() }
                coEvery {
                    sykmeldingStatusService.registrerStatus(
                        match { it.event == StatusEvent.AVBRUTT }
                    )
                } answers
                    {
                        callOriginal()
                        applicationState.alive = false
                        applicationState.ready = false
                    }
                val apenEvent = getApenEvent(sykmelding)
                kafkaProducer.send(apenEvent, sykmelding.pasientFnr)
                val tidligereArbeidsgiver1 = TidligereArbeidsgiverKafkaDTO("ag1", "orgnummer", "1")
                kafkaProducer.send(
                    getSykmeldingBekreftEvent(
                        sykmelding,
                        tidligereArbeidsgiver1,
                        apenEvent.timestamp.plusMinutes(1)
                    ),
                    sykmelding.pasientFnr
                )

                kafkaProducer.send(
                    getSykmeldingAvbruttEvent(sykmelding.id, apenEvent.timestamp.plusMinutes(20)),
                    sykmelding.pasientFnr
                )
                runBlocking { sykmeldingStatusConsumerService.start() }

                val tidligereArbeidsgiverList =
                    database.connection.getTidligereArbeidsgiver(sykmelding.id)
                tidligereArbeidsgiverList.size shouldBeEqualTo 0
            }
        }

        context("Test Kafka -> DB -> status API") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                application.routing {
                    route("/api/v3") { registrerSykmeldingApiV2(sykmeldingerService) }
                }
                test("test get sykmelding with latest status SENDT") {
                    val sendtEvent =
                        publishSendAndWait(
                            sykmeldingStatusService,
                            applicationState,
                            kafkaProducer,
                            sykmelding,
                            sykmeldingStatusConsumerService
                        )
                    with(
                        handleRequest(HttpMethod.Get, "/api/v3/sykmeldinger") {
                            call.authentication.principal(
                                BrukerPrincipal("pasientFnr", JWTPrincipal(mockPayload))
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        val sykmeldingDTO =
                            objectMapper.readValue<List<SykmeldingDTO>>(response.content!!)[0]
                        val latestSykmeldingStatus = sykmeldingDTO.sykmeldingStatus
                        latestSykmeldingStatus shouldBeEqualTo
                            no.nav.syfo.sykmelding.model.SykmeldingStatusDTO(
                                timestamp = sendtEvent.timestamp,
                                sporsmalOgSvarListe =
                                    listOf(
                                        SporsmalDTO(
                                            tekst = "din arbeidssituasjon?",
                                            svar =
                                                SvarDTO(
                                                    no.nav.syfo.sykmelding.model.SvartypeDTO
                                                        .ARBEIDSSITUASJON,
                                                    "ARBEIDSTAKER",
                                                ),
                                            shortName =
                                                no.nav.syfo.sykmelding.model.ShortNameDTO
                                                    .ARBEIDSSITUASJON,
                                        ),
                                    ),
                                arbeidsgiver =
                                    no.nav.syfo.sykmelding.status.api.ArbeidsgiverStatusDTO(
                                        "org",
                                        "jorg",
                                        "navn"
                                    ),
                                statusEvent = "SENDT",
                            )
                    }
                }
            }
        }
    })

fun getSlettetEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmelding.id,
        getNowTickMillisOffsetDateTime().plusMonths(1),
        STATUS_SLETTET,
        null,
        null,
        brukerSvar = null,
    )
}

private fun setUpEnvironment(environment: Environment) {
    every { environment.applicationName } returns "KafkaStatusIntegrationTest-application"
    every { environment.sykmeldingStatusAivenTopic } returns "KafkaStatusIntegrationTest-topic"
    every { environment.sendSykmeldingKafkaTopic } returns
        "KafkaStatusIntegrationTest-sendt-sykmelding-topic"
    every { environment.bekreftSykmeldingKafkaTopic } returns
        "KafkaStatusIntegrationTest-syfo-bekreftet-sykmelding"
    every { environment.mottattSykmeldingKafkaTopic } returns
        "KafkaStatusIntegrationTest-syfo-mottatt-sykmelding"
    every { environment.cluster } returns "localhost"
    every { environment.okSykmeldingTopic } returns "KafkaStatusIntegrationTestoksykmeldingtopic"
    every { environment.avvistSykmeldingTopic } returns
        "KafkaStatusIntegrationTestavvisttopiclAiven"
    every { environment.manuellSykmeldingTopic } returns "KafkaStatusIntegrationTestmanuelltopic"
    every { environment.mottattSykmeldingKafkaTopic } returns
        "KafkaStatusIntegrationTestmanuelltopic"
}

@DelicateCoroutinesApi
private fun publishSendAndWait(
    sykmeldingStatusService: SykmeldingStatusService,
    applicationState: ApplicationState,
    kafkaProducer: SykmeldingStatusKafkaProducer,
    sykmelding: Sykmeldingsopplysninger,
    sykmeldingStatusConsumerService: SykmeldingStatusConsumerService,
    sendTimestamp: OffsetDateTime =
        sykmelding.mottattTidspunkt.plusMinutes(1).atOffset(ZoneOffset.UTC)
): SykmeldingStatusKafkaEventDTO {
    coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers
        {
            callOriginal()
            applicationState.alive = false
            applicationState.ready = false
        }
    kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
    val sendtEvent = getSendtEvent(sykmelding, sendTimestamp)
    kafkaProducer.send(sendtEvent, sykmelding.pasientFnr)
    val job = createListener(applicationState) { sykmeldingStatusConsumerService.start() }
    runBlocking { job.join() }
    return sendtEvent
}

private fun getSykmeldingBekreftEvent(
    sykmelding: Sykmeldingsopplysninger,
    tidligereArbeidsgiverDTO: TidligereArbeidsgiverKafkaDTO? = null,
    timestamp: OffsetDateTime = sykmelding.mottattTidspunkt.plusMinutes(1).atOffset(ZoneOffset.UTC),
): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        statusEvent = STATUS_BEKREFTET,
        timestamp = timestamp,
        arbeidsgiver = null,
        sporsmals =
            listOf(
                SporsmalOgSvarKafkaDTO(
                    "sporsmal",
                    ShortNameKafkaDTO.FORSIKRING,
                    SvartypeKafkaDTO.JA_NEI,
                    "NEI"
                )
            ),
        tidligereArbeidsgiver = tidligereArbeidsgiverDTO,
        brukerSvar = createKomplettInnsendtSkjemaSvar(),
    )
}

fun getSykmeldingAvbruttEvent(
    id: String,
    timestamp: OffsetDateTime = getNowTickMillisOffsetDateTime()
) =
    SykmeldingStatusKafkaEventDTO(
        sykmeldingId = id,
        timestamp = timestamp,
        arbeidsgiver = null,
        sporsmals = null,
        statusEvent = STATUS_AVBRUTT,
        brukerSvar = createKomplettInnsendtSkjemaSvar(),
    )

private fun getSendtEvent(
    sykmelding: Sykmeldingsopplysninger,
    timestamp: OffsetDateTime = sykmelding.mottattTidspunkt.plusMinutes(1).atOffset(ZoneOffset.UTC)
): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        timestamp = timestamp,
        arbeidsgiver = ArbeidsgiverStatusKafkaDTO("org", "jorg", "navn"),
        statusEvent = STATUS_SENDT,
        sporsmals =
            listOf(
                SporsmalOgSvarKafkaDTO(
                    "din arbeidssituasjon?",
                    ShortNameKafkaDTO.ARBEIDSSITUASJON,
                    SvartypeKafkaDTO.ARBEIDSSITUASJON,
                    "ARBEIDSTAKER"
                ),
            ),
        brukerSvar = createKomplettInnsendtSkjemaSvar(),
    )
}

private fun getApenEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmelding.id,
        timestamp = sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC),
        statusEvent = STATUS_APEN,
        brukerSvar = null
    )
}
