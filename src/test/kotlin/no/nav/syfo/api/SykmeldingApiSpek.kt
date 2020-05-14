package no.nav.syfo.api

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.aksessering.api.BehandlingsutfallStatusDTO
import no.nav.syfo.aksessering.api.FullstendigSykmeldingDTO
import no.nav.syfo.aksessering.api.PeriodetypeDTO
import no.nav.syfo.aksessering.api.SkjermetSykmeldingDTO
import no.nav.syfo.aksessering.api.registerSykmeldingApi
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.StatusEventDTO
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingBekreftEvent
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmelding.status.api.ShortNameDTO
import no.nav.syfo.sykmelding.status.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmelding.status.api.SvartypeDTO
import no.nav.syfo.sykmelding.status.api.lagSporsmalListe
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.sykmelding.status.registrerBekreftet
import no.nav.syfo.sykmelding.status.registrerSendt
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SykmeldingApiSpek : Spek({

    val database = TestDB()
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusKafkaProducer::class)

    fun lagreApenStatus(id: String, mottattTidspunkt: OffsetDateTime) {
        database.registerStatus(SykmeldingStatusEvent(id, mottattTidspunkt, StatusEvent.APEN))
    }
    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        lagreApenStatus(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        clearAllMocks()
        every { sykmeldingStatusKafkaProducer.send(any(), any()) } returns Unit
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    val mockPayload = mockk<Payload>()

    describe("SykmeldingApi") {
        with(TestApplicationEngine()) {
            start()
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.routing {
                registerSykmeldingApi(SykmeldingService(database), sykmeldingStatusKafkaProducer)
            }

            it("skal returnere tom liste hvis bruker ikke har sykmeldinger") {
                every { mockPayload.subject } returns "AnnetPasientFnr"

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SkjermetSykmeldingDTO>>(response.content!!) shouldEqual emptyList()
                }
            }

            it("skal hente sykmeldinger for bruker") {
                every { mockPayload.subject } returns "pasientFnr"

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingsperioder[0]
                        .type shouldEqual PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                    fullstendigSykmeldingDTO.medisinskVurdering.hovedDiagnose
                        ?.diagnosekode shouldEqual testSykmeldingsdokument.sykmelding.medisinskVurdering.hovedDiagnose?.kode
                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.APEN
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe shouldEqual null
                }
            }

            it("skal ikke sende med medisinsk vurdering hvis sykmeldingen er skjermet") {
                database.lagreMottattSykmelding(
                    testSykmeldingsopplysninger.copy(
                        id = "uuid2",
                        pasientFnr = "PasientFnr1"
                    ),
                    testSykmeldingsdokument.copy(
                        id = "uuid2",
                        sykmelding = testSykmeldingsdokument.sykmelding.copy(
                            id = "id2",
                            skjermesForPasient = true
                        )
                    ))
                lagreApenStatus("uuid2", testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC))
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

                every { mockPayload.subject } returns "PasientFnr1"

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SkjermetSykmeldingDTO>>(response.content!!)[0]
                        .sykmeldingsperioder[0]
                        .type shouldEqual PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                }
            }

            it("Skal ikke returnere statustekster som er sendt til manuell behandling") {
                database.lagreMottattSykmelding(testSykmeldingsopplysninger.copy(id = "uuid2", pasientFnr = "123"), testSykmeldingsdokument.copy(id = "uuid2"))
                lagreApenStatus("uuid2", testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC))
                database.connection.opprettBehandlingsutfall(
                    Behandlingsutfall("uuid2",
                        ValidationResult(
                            Status.MANUAL_PROCESSING,
                            listOf(RuleInfo("INFOTRYGD", "INFORTRYGD", "INFOTRYGD", Status.MANUAL_PROCESSING))
                        )
                    )
                )
                every { mockPayload.subject } returns "123"

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val sykmeldinger = objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]
                    sykmeldinger.behandlingsutfall.ruleHits shouldEqual emptyList()
                    sykmeldinger.behandlingsutfall.status shouldEqual BehandlingsutfallStatusDTO.MANUAL_PROCESSING
                }
            }

            it("Skal vise tekster når behandlingsutfall er avvist") {
                database.lagreMottattSykmelding(testSykmeldingsopplysninger.copy(id = "uuid2", pasientFnr = "123"), testSykmeldingsdokument.copy(id = "uuid2"))
                lagreApenStatus("uuid2", testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC))
                database.connection.opprettBehandlingsutfall(
                    Behandlingsutfall("uuid2",
                        ValidationResult(
                            Status.INVALID,
                            listOf(RuleInfo("TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING", "Fyll ut punkt 11.2", "Sykmeldingen er tilbakedatert uten begrunnelse fra den som sykmeldte deg", Status.INVALID))
                        )
                    )
                )
                every { mockPayload.subject } returns "123"

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val sykmelding = objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]
                    sykmelding.behandlingsutfall.status shouldEqual BehandlingsutfallStatusDTO.INVALID
                    sykmelding.behandlingsutfall.ruleHits[0].messageForUser shouldEqual "Sykmeldingen er tilbakedatert uten begrunnelse fra den som sykmeldte deg"
                }
            }

            it("Skal Kunne bekrefte sine sykmeldinger") {
                every { mockPayload.subject } returns "pasientFnr"

                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/uuid/bekreft") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    verify(exactly = 1) { sykmeldingStatusKafkaProducer.send(any(), any()) }
                }
            }

            it("Skal ikke kunne bekrefte andre sine sykmeldinger") {

                every { mockPayload.subject } returns "123"

                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/uuid/bekreft") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    verify(exactly = 0) { sykmeldingStatusKafkaProducer.send(any(), any()) }
                }
            }

            it("Skal få med arbeidsgiver hvis sykmelding er sendt") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registrerSendt(SykmeldingSendEvent("uuid", timestamp,
                    ArbeidsgiverStatus(
                        "uuid", "orgnummer", null, "Bedrift A/S"
                    ),
                    Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON,
                        Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))),
                    SykmeldingStatusEvent("uuid", timestamp, StatusEvent.SENDT))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.SENDT
                    fullstendigSykmeldingDTO.sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual ArbeidsgiverStatusDTO("orgnummer", null, "Bedrift A/S")
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe?.size shouldEqual 1
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe!![0] shouldEqual SporsmalOgSvarDTO("Arbeidssituasjon", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER")
                }
            }

            it("Skal få med spørsmål og svar hvis sykmelding er bekreftet") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registrerBekreftet(SykmeldingBekreftEvent("uuid", timestamp,
                    lagSporsmalListe("uuid")),
                    SykmeldingStatusEvent("uuid", timestamp, StatusEvent.BEKREFTET))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
                    fullstendigSykmeldingDTO.sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe?.size shouldEqual 4
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe!![0] shouldEqual SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser")
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe!![1] shouldEqual SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja")
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe!![2] shouldEqual SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja")
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe!![3] shouldEqual SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}")
                }
            }

            it("Skal ikke få spørsmål/svar eller arbeidsgiver for sykmelding som er bekreftet uten spm/svar") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registrerBekreftet(SykmeldingBekreftEvent("uuid", timestamp, null),
                    SykmeldingStatusEvent("uuid", timestamp, StatusEvent.BEKREFTET))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
                    fullstendigSykmeldingDTO.sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe shouldEqual null
                }
            }

            it("Skal takle at spørsmål/svar for sykmelding som er sendt mangler") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registerStatus(SykmeldingStatusEvent("uuid", timestamp, StatusEvent.SENDT))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.SENDT
                    fullstendigSykmeldingDTO.sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe shouldEqual null
                }
            }

            it("Skal ikke få spørsmål/svar eller arbeidsgiver for sykmelding som er endret og bekreftet på nytt uten spm/svar") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registrerBekreftet(SykmeldingBekreftEvent("uuid", timestamp.minusMinutes(2),
                    lagSporsmalListe("uuid")),
                    SykmeldingStatusEvent("uuid", timestamp.minusMinutes(2), StatusEvent.BEKREFTET))
                database.registrerBekreftet(SykmeldingBekreftEvent("uuid", timestamp, null),
                    SykmeldingStatusEvent("uuid", timestamp, StatusEvent.BEKREFTET))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTO =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    fullstendigSykmeldingDTO.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
                    fullstendigSykmeldingDTO.sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTO.sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTO.sykmeldingStatus.sporsmalOgSvarListe shouldEqual null
                }
            }

            it("Henter riktige statuser for flere sykmeldinger") {
                every { mockPayload.subject } returns "pasientFnr"
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                database.registrerSendt(SykmeldingSendEvent("uuid", timestamp,
                    ArbeidsgiverStatus(
                        "uuid", "orgnummer", null, "Bedrift A/S"
                    ),
                    Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON,
                        Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))),
                    SykmeldingStatusEvent("uuid", timestamp, StatusEvent.SENDT))
                lagreApenStatus("uuid2", testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC))
                database.lagreMottattSykmelding(testSykmeldingsopplysninger.copy(id = "uuid2"), testSykmeldingsdokument.copy(id = "uuid2", sykmelding = testSykmeldingsdokument.sykmelding.copy(id = "id2")))
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val fullstendigSykmeldingDTOListe =
                        objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)

                    fullstendigSykmeldingDTOListe.size shouldEqual 2
                    fullstendigSykmeldingDTOListe[0].id shouldEqual "uuid2"
                    fullstendigSykmeldingDTOListe[0].sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.APEN
                    fullstendigSykmeldingDTOListe[0].sykmeldingStatus.arbeidsgiver shouldEqual null
                    fullstendigSykmeldingDTOListe[0].sykmeldingStatus.sporsmalOgSvarListe shouldEqual null
                    fullstendigSykmeldingDTOListe[1].id shouldEqual "uuid"
                    fullstendigSykmeldingDTOListe[1].sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.SENDT
                    fullstendigSykmeldingDTOListe[1].sykmeldingStatus.timestamp shouldEqual timestamp
                    fullstendigSykmeldingDTOListe[1].sykmeldingStatus.arbeidsgiver shouldEqual ArbeidsgiverStatusDTO("orgnummer", null, "Bedrift A/S")
                    fullstendigSykmeldingDTOListe[1].sykmeldingStatus.sporsmalOgSvarListe?.size shouldEqual 1
                    fullstendigSykmeldingDTOListe[1].sykmeldingStatus.sporsmalOgSvarListe!![0] shouldEqual SporsmalOgSvarDTO("Arbeidssituasjon", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER")
                }
            }
        }
    }
})
