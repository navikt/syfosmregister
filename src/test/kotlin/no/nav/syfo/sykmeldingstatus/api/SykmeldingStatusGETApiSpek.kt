package no.nav.syfo.sykmeldingstatus.api

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.install
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import java.time.LocalDateTime
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.model.SykmeldingStatusEventDTO
import no.nav.syfo.testutil.setUpContentNegotiation
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusGETApiSpek : Spek({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val mockPayload = mockk<Payload>()

    beforeEachTest {
        clearAllMocks()
        every { mockPayload.subject } returns "pasient_fnr"
        every { sykmeldingStatusService.erEier(any(), any()) } returns true
    }

    describe("Get SykmeldingStatus") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation, setUpContentNegotiation())
            application.routing { registerSykmeldingStatusGETApi(sykmeldingStatusService) }

            it("Should get all statuses sykmeldingstatus") {
                val timestamp = LocalDateTime.now()
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                        SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                        SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT))

                with(handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=ALL") {
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldEqual 2
                }
            }

            it("Should get all statuses without filter") {
                val timestamp = LocalDateTime.now()
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                        SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                        SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT))

                with(handleRequest(HttpMethod.Get, "/sykmeldinger/123/status") {
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldEqual 2
                }
            }

            it("Should get latest status") {
                val timestamp = LocalDateTime.now()
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT))

                with(handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldEqual 1
                    responseData.get(0).statusEvent == StatusEventDTO.SENDT
                }
            }

            it("Should get forbidden when not owner") {
                val timestamp = LocalDateTime.now()
                every { sykmeldingStatusService.erEier(any(), any()) } returns false
                with(handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    call.authentication.principal = JWTPrincipal(mockPayload)
                }) {
                    response.status() shouldEqual HttpStatusCode.Forbidden
                }
            }
        }
    }
})
