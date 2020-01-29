package no.nav.syfo.sykmelding.internal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.mockkClass
import java.time.LocalDateTime
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.internal.api.registrerInternalSykmeldingApi
import no.nav.syfo.sykmelding.internal.model.InternalSykmeldingDTO
import no.nav.syfo.sykmelding.internal.service.InternalSykmeldingService
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class InternalSykmeldingIntegrationTest : Spek({

    val database = TestDB()

    database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument, SykmeldingStatusEvent(testSykmeldingsopplysninger.id, LocalDateTime.now(), StatusEvent.APEN))
    database.connection.opprettBehandlingsutfall(testBehandlingsutfall)

    val internalSykmeldingService = InternalSykmeldingService(database)
    val tilgangskontrollService = mockkClass(TilgangskontrollService::class)
    coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns true
    describe("Test get InternalSykmelding") {
        it("Should be able to get sykmelidng") {
            with(TestApplicationEngine()) {
                start(true)
                application.install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                application.routing { registrerInternalSykmeldingApi(internalSykmeldingService, tilgangskontrollService) }
                with(handleRequest(HttpMethod.Get, "/api/v1/internal/sykmeldinger?fnr=pasientFnr") {
                    addHeader("accept", "application/json")
                    addHeader("Authentication", "Bearer 123")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<InternalSykmeldingDTO>>(response.content!!) shouldNotEqual null
                }
            }
        }
    }
})
