package no.nav.syfo.sykmeldingstatus

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.spyk
import java.time.ZoneOffset
import java.util.Properties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.aksessering.api.FullstendigSykmeldingDTO
import no.nav.syfo.aksessering.api.registerSykmeldingApi
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.createListener
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.model.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingStatusGETApi
import no.nav.syfo.sykmeldingstatus.kafka.KafkaFactory
import no.nav.syfo.sykmeldingstatus.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaDeserializer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.setUpTestApplication
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import no.nav.syfo.util.TimestampUtil.Companion.getAdjustedToLocalDateTime
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer

class KafkaStatusIntegrationTest : Spek({

    val database = TestDB()

    val kafka = KafkaContainer()
    kafka.start()
    val environment = mockkClass(Environment::class)
    every { environment.applicationName } returns "application"
    every { environment.sykmeldingStatusTopic } returns "topic"

    fun setupKafkaConfig(): Properties {
        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonKafkaDeserializer::class.java
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonKafkaSerializer::class.java
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        return kafkaConfig
    }
    val sykmelding = testSykmeldingsopplysninger
    val kafkaConfig = setupKafkaConfig()
    val kafkaProducer = KafkaFactory.getSykmeldingStatusKafkaProducer(kafkaConfig, environment)
    var applicationState = ApplicationState(alive = true, ready = true)
    val sykmeldingStatusService = spyk(SykmeldingStatusService(database))
    val consumer = KafkaFactory.getKafkaStatusConsumer(kafkaConfig, environment)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusService, consumer, applicationState)
    val sykmeldingService = SykmeldingService(database)
    val mockPayload = mockk<Payload>()

    afterGroup {
        kafka.stop()
        applicationState.ready = false
        applicationState.alive = false
    }

    beforeEachTest {
        applicationState.alive = true
        applicationState.ready = true
        clearAllMocks()
        every { environment.applicationName } returns "application"
        every { environment.sykmeldingStatusTopic } returns "topic"
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any()) } returns Unit
        database.lagreMottattSykmelding(sykmelding, testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        every { mockPayload.subject } returns "pasientFnr"
    }

    afterEachTest {
        database.connection.dropData()
    }

    describe("Read from status topic and save in DB") {
        it("Write and read APEN status") {

            every { sykmeldingStatusService.registrerStatus(any()) } answers {
                callOriginal()
                applicationState.alive = false
                applicationState.ready = false
            }

            kafkaProducer.send(getApenEvent(sykmelding),
                    testSykmeldingsopplysninger.pasientFnr)

            runBlocking {
                this.launch {
                    sykmeldingStatusConsumerService.start()
                }
            }
            val sykmeldinger = database.hentSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldEqual 1
            sykmeldinger.get(0).sykmeldingStatus shouldEqual SykmeldingStatus(
                    timestamp = getAdjustedToLocalDateTime(sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC)),
                    statusEvent = StatusEvent.APEN,
                    arbeidsgiver = null,
                    sporsmalListe = null
            )
            database.hentSykmeldingStatuser(sykmelding.id).size shouldEqual 1
        }

        it("write and read APEN and SENDT") {

            every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
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
            val sykmeldinger = database.hentSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldEqual 1
            val sykmeldingstatus = sykmeldinger.get(0).sykmeldingStatus
            sykmeldingstatus shouldEqual SykmeldingStatus(
                    timestamp = getAdjustedToLocalDateTime(sendEvent.timestamp),
                    statusEvent = StatusEvent.SENDT,
                    arbeidsgiver = ArbeidsgiverStatus(sykmelding.id, "org", "jorg", "navn"),
                    sporsmalListe = listOf(Sporsmal("din arbeidssituasjon?", ShortName.ARBEIDSSITUASJON, Svar(
                            sykmelding.id, sykmeldingstatus.sporsmalListe?.get(0)?.svar?.sporsmalId, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"
                    ))))

            database.hentSykmeldingStatuser(sykmelding.id).size shouldEqual 2
        }

        it("Should test APEN and BEKREFTET") {
            every { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
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
            val sykmeldinger = database.hentSykmeldinger(sykmelding.pasientFnr)
            sykmeldinger.size shouldEqual 1
            val sykmeldingStatus = sykmeldinger.get(0).sykmeldingStatus
            sykmeldingStatus shouldEqual SykmeldingStatus(
                    timestamp = getAdjustedToLocalDateTime(bekreftetEvent.timestamp),
                    statusEvent = StatusEvent.BEKREFTET,
                    arbeidsgiver = null,
                    sporsmalListe = listOf(Sporsmal("sporsmal", ShortName.FORSIKRING, Svar(
                            sykmelding.id, sykmeldingStatus.sporsmalListe?.get(0)?.svar?.sporsmalId, Svartype.JA_NEI, "NEI"
                    ))))

            database.hentSykmeldingStatuser(sykmelding.id).size shouldEqual 2
        }
    }

    describe("Test Kafka -> DB -> status API") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registerSykmeldingStatusGETApi(sykmeldingStatusService) }
            application.routing { registerSykmeldingApi(sykmeldingService, kafkaProducer) }
            it("Test get stykmeldingstatus latest should be SENDT") {
                every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
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
                with(handleRequest(HttpMethod.Get, "/sykmeldinger/uuid/status?filter=LATEST") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val sykmeldingStatuser = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    sykmeldingStatuser.size shouldEqual 1
                    val latestSykmeldingStatus = sykmeldingStatuser.get(0)
                    latestSykmeldingStatus shouldEqual SykmeldingStatusEventDTO(
                            no.nav.syfo.sykmeldingstatus.StatusEventDTO.SENDT,
                            sendtEvent.timestamp
                    )
                }
            }

            it("test get sykmelding with latest status SENDT") {
                every { sykmeldingStatusService.registrerSendt(any(), any()) } answers {
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
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO: FullstendigSykmeldingDTO = objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]
                    val latestSykmeldingStatus = fullstendigSykmeldingDTO.sykmeldingStatus
                    latestSykmeldingStatus shouldEqual SykmeldingStatusDTO(
                            timestamp =  getAdjustedToLocalDateTime(sendtEvent.timestamp),
                            sporsmalOgSvarListe = listOf(no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO(
                                    tekst = "din arbeidssituasjon?",
                                    svar = "ARBEIDSTAKER",
                                    svartype = no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.ARBEIDSSITUASJON,
                                    shortName = no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.ARBEIDSSITUASJON
                            )),
                            arbeidsgiver = no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO("org", "jorg", "navn"),
                            statusEvent = no.nav.syfo.sykmeldingstatus.StatusEventDTO.SENDT

                    )
                }
            }
        }
    }

    describe("Test bereft API -> Kafka -> DB -> API") {
        it("Should test BEKREFT API") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                application.routing { registerSykmeldingApi(sykmeldingService, kafkaProducer) }
                every { sykmeldingStatusService.registrerBekreftet(any(), any()) } answers {
                    callOriginal()
                    applicationState.alive = false
                    applicationState.ready = false
                }

                kafkaProducer.send(getApenEvent(sykmelding), sykmelding.pasientFnr)

                val job = createListener(applicationState) {
                    sykmeldingStatusConsumerService.start()
                }

                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/uuid/bekreft") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
                runBlocking {
                    job.join()
                }
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO: FullstendigSykmeldingDTO = objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]
                    fullstendigSykmeldingDTO.bekreftetDato shouldNotEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus shouldEqual SykmeldingStatusDTO(
                            timestamp = fullstendigSykmeldingDTO.bekreftetDato!!,
                            arbeidsgiver = null,
                            statusEvent = no.nav.syfo.sykmeldingstatus.StatusEventDTO.BEKREFTET,
                            sporsmalOgSvarListe = null
                    )
                }
            }
        }
    }
})

private fun getSykmeldingBekreftEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmelding.id,
            statusEvent = StatusEventDTO.BEKREFTET,
            timestamp = sykmelding.mottattTidspunkt.plusHours(1).atOffset(ZoneOffset.UTC),
            arbeidsgiver = null,
            sporsmals = listOf(SporsmalOgSvarDTO("sporsmal", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "NEI")))
}

private fun getSendtEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmelding.id,
            timestamp = sykmelding.mottattTidspunkt.plusHours(1).atOffset(ZoneOffset.UTC),
            arbeidsgiver = ArbeidsgiverStatusDTO("org", "jorg", "navn"),
            statusEvent = StatusEventDTO.SENDT,
            sporsmals = listOf(SporsmalOgSvarDTO("din arbeidssituasjon?", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER")
            ))
}

private fun getApenEvent(sykmelding: Sykmeldingsopplysninger): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
            sykmeldingId = sykmelding.id,
            timestamp = sykmelding.mottattTidspunkt.atOffset(ZoneOffset.UTC),
            statusEvent = StatusEventDTO.APEN)
}
