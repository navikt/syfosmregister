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
import io.mockk.every
import io.mockk.mockk
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
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
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

    afterGroup {
        database.stop()
    }

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
                registerSykmeldingApi(SykmeldingService(database), SykmeldingStatusService(database))
            }

            beforeEachTest {
                database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
                database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
                database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
            }

            afterEachTest {
                database.connection.dropData()
            }

            val mockPayload = mockk<Payload>()

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
                    val actual =
                            objectMapper.readValue<List<FullstendigSykmeldingDTO>>(response.content!!)[0]

                    actual.sykmeldingsperioder[0]
                            .type shouldEqual PeriodetypeDTO.AKTIVITET_IKKE_MULIG
                    actual.medisinskVurdering.hovedDiagnose
                            ?.diagnosekode shouldEqual testSykmeldingsdokument.sykmelding.medisinskVurdering.hovedDiagnose?.kode
                }
            }

            it("skal ikke sende med medisinsk vurdering hvis sykmeldingen er skjermet") {
                database.connection.opprettSykmeldingsopplysninger(
                        testSykmeldingsopplysninger.copy(
                                id = "uuid2",
                                pasientFnr = "PasientFnr1"
                        )
                )
                database.connection.opprettSykmeldingsdokument(
                        testSykmeldingsdokument.copy(
                                id = "uuid2",
                                sykmelding = testSykmeldingsdokument.sykmelding.copy(
                                        id = "id2",
                                        skjermesForPasient = true
                                )
                        )
                )
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
                database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger.copy(id = "uuid2", pasientFnr = "123"))
                database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument.copy(id = "uuid2"))
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
                database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger.copy(id = "uuid2", pasientFnr = "123"))
                database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument.copy(id = "uuid2"))
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

                with(handleRequest(HttpMethod.Post, "/api/v1//sykmeldinger/uuid/bekreft") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
            it("Skal ikke kunne bekrefte andre sine sykmeldinger") {

                every { mockPayload.subject } returns "123"

                with(handleRequest(HttpMethod.Post, "/api/v1//sykmeldinger/uuid/bekreft") {
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                }
            }
        }
    }
})
