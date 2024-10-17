package no.nav.syfo.sykmelding.status

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.server.auth.*
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import io.mockk.*
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.setupAuth
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@DelicateCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class KafkaStatusIntegrationTest {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()

    val database = TestDB.database

    val environment = mockkClass(Environment::class)

    init {
        setUpEnvironment(environment)
    }

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
            database,
        )
    val sykmeldingStatusConsumerService =
        SykmeldingStatusConsumerService(consumer, applicationState, mottattSykmeldingStatusService)
    val sykmeldingerService = SykmeldingerService(database)

    @AfterEach
    fun afterTest() {
        applicationState.alive = false
        applicationState.ready = false
        database.connection.dropData()
    }

    @BeforeEach
    fun beforeTest() {
        applicationState.alive = true
        applicationState.ready = true
        clearAllMocks()
        setUpEnvironment(environment)
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
        database.connection.dropData()
        runBlocking {
            database.lagreMottattSykmelding(sykmelding, testSykmeldingsdokument)
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        }
    }

    @Test
    internal fun `Read from status topic and save in DB Write and read APEN status`() {
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

        runBlocking {
            sykmeldingStatusConsumerService.start()

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
    }

    @Test
    internal fun `Read from status topic and save in DB write and read APEN and SENDT`() {
        coEvery { sykmeldingStatusService.registrerSendt(any(), any()) } answers
            {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

        kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
        val sendEvent = getSendtEvent(sykmelding)
        kafkaProducer.send(sendEvent, sykmelding.pasientFnr)

        runBlocking {
            sykmeldingStatusConsumerService.start()

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
    }

    @Test
    internal fun `Read from status topic and save in DB Should test APEN and BEKREFTET`() {
        coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
            {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

        kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
        val bekreftetEvent = getSykmeldingBekreftEvent(sykmelding)
        kafkaProducer.send(bekreftetEvent, sykmelding.pasientFnr)

        runBlocking {
            sykmeldingStatusConsumerService.start()

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
    }

    @Test
    internal fun `Read from status topic and save in DB Should test APEN and SLETTET`() {
        coEvery { sykmeldingStatusService.slettSykmelding(any()) } answers
            {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }
        kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
        kafkaProducer.send(getSlettetEvent(sykmelding), sykmelding.pasientFnr)

        runBlocking {
            sykmeldingStatusConsumerService.start()

            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Read from status topic and save in DB should test APEN  SENDT SLETTET`() {
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
        runBlocking {
            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
            database.finnSvarForSykmelding(sykmelding.id).size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Read from status topic and save in DB should test APEN  BEKREFTET SLETTET`() {
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
        runBlocking {
            val sykmeldinger = database.getSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Read from status topic and save in DB lagrer tidligereArbeidsgiver i databasen`() {
        coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
            {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

        kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)
        val tidligereArbeidsgiver = TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
        kafkaProducer.send(
            getSykmeldingBekreftEvent(sykmelding, tidligereArbeidsgiver),
            sykmelding.pasientFnr,
        )

        runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

        runBlocking {
            val tidligereArbeidsgiverList =
                database.connection.getTidligereArbeidsgiver(sykmelding.id)

            tidligereArbeidsgiverList.size shouldBeEqualTo 1
        }
    }

    @Test
    internal fun `Read from status topic and save in DB sletter tidligere arbeidsgiver fra basen`() {
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
        val tidligereArbeidsgiver = TidligereArbeidsgiverKafkaDTO("orgNavn", "orgnummer", "1")
        kafkaProducer.send(
            getSykmeldingBekreftEvent(sykmelding, tidligereArbeidsgiver),
            sykmelding.pasientFnr,
        )

        runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

        runBlocking {
            var tidligereArbeidsgiverList =
                database.connection.getTidligereArbeidsgiver(sykmelding.id)

            tidligereArbeidsgiverList.size shouldBeEqualTo 1

            kafkaProducer.send(getSendtEvent(sykmelding), sykmelding.pasientFnr)
            applicationState.alive = true
            applicationState.ready = true

            runBlocking { sykmeldingStatusConsumerService.start() }
            tidligereArbeidsgiverList = database.connection.getTidligereArbeidsgiver(sykmelding.id)
            tidligereArbeidsgiverList.size shouldBeEqualTo 0
        }
    }

    @Test
    internal fun `Read from status topic and save in DB sletter bekreftet etter bekreftet`() {

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
                timestamp = apenEvent.timestamp.plusMinutes(1),
            ),
            sykmelding.pasientFnr,
        )
        val tidligereArbeidsgiver2 = TidligereArbeidsgiverKafkaDTO("ag2", "orgnummer", "1")

        kafkaProducer.send(
            getSykmeldingBekreftEvent(
                sykmelding,
                tidligereArbeidsgiver2,
                apenEvent.timestamp.plusMinutes(1),
            ),
            sykmelding.pasientFnr,
        )
        runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }

        runBlocking {
            val tidligereArbeidsgiverList =
                database.connection.getTidligereArbeidsgiver(sykmelding.id)

            tidligereArbeidsgiverList.size shouldBeEqualTo 1
            tidligereArbeidsgiverList.singleOrNull()?.tidligereArbeidsgiver?.orgNavn shouldBeEqualTo
                tidligereArbeidsgiver2.orgNavn
            tidligereArbeidsgiverList
                .singleOrNull()
                ?.tidligereArbeidsgiver
                ?.orgnummer shouldBeEqualTo tidligereArbeidsgiver2.orgnummer
            tidligereArbeidsgiverList
                .singleOrNull()
                ?.tidligereArbeidsgiver
                ?.sykmeldingsId shouldBeEqualTo tidligereArbeidsgiver2.sykmeldingsId
        }
    }

    @Test
    internal fun `Read from status topic and save in DB sletter avbrutt etter bekreftet`() {
        coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any()) } answers
            {
                callOriginal()
            }
        coEvery { sykmeldingStatusService.registrerStatus(any()) } answers
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
                apenEvent.timestamp.plusMinutes(1),
            ),
            sykmelding.pasientFnr,
        )

        kafkaProducer.send(
            getSykmeldingAvbruttEvent(sykmelding.id, apenEvent.timestamp.plusMinutes(2)),
            sykmelding.pasientFnr,
        )
        runBlocking { this.launch { sykmeldingStatusConsumerService.start() } }
        runBlocking {
            val tidligereArbeidsgiverList =
                database.connection.getTidligereArbeidsgiver(sykmelding.id)
            tidligereArbeidsgiverList.size shouldBeEqualTo 0
        }
    }

    @Disabled
    @Test
    internal fun `Test Kafka  DB  status API test get sykmelding with latest status SENDT`() {
        // TODO why this no work?
        testApplication {
            setUpTestApplication()
            val sendtEvent =
                publishSendAndWait(
                    sykmeldingStatusService,
                    applicationState,
                    kafkaProducer,
                    sykmelding,
                    sykmeldingStatusConsumerService
                )
            application {
                setupAuth(
                    jwkProvider,
                    "tokenXissuer",
                    jwkProvider,
                    getEnvironment(),
                )
                routing {
                    route("/api/v3") {
                        authenticate("tokenx") {
                            registrerSykmeldingApiV2(sykmeldingerService = sykmeldingerService)
                        }
                    }
                }
            }

            val response =
                client.get("/api/v3/sykmeldinger") {
                    headers {
                        append(
                            Authorization,
                            "Bearer ${generateJWT("", "clientid", subject = "pasientFnr")}",
                        )
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.OK

            val sykmeldingDTO =
                objectMapper.readValue<List<SykmeldingDTO>>(response.bodyAsText())[0]
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
                                        no.nav.syfo.sykmelding.model.SvartypeDTO.ARBEIDSSITUASJON,
                                        "ARBEIDSTAKER",
                                    ),
                                shortName =
                                    no.nav.syfo.sykmelding.model.ShortNameDTO.ARBEIDSSITUASJON,
                            ),
                        ),
                    arbeidsgiver =
                        no.nav.syfo.sykmelding.status.api.ArbeidsgiverStatusDTO(
                            "org",
                            "jorg",
                            "navn",
                        ),
                    statusEvent = "SENDT",
                )


        }
    }
}

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
    every { environment.behandlingsUtfallTopic } returns
        "KafkaStatusIntegrationTestbehandlingsutfallAiven"
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
                    "NEI",
                ),
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
                    "ARBEIDSTAKER",
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
        brukerSvar = null,
    )
}
