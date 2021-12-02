package no.nav.syfo.sykmelding.status.api

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
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.StatusEventDTO
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.status.api.model.SykmeldingStatusEventDTO
import no.nav.syfo.testutil.setUpContentNegotiation
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                    SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                    SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT)
                )

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=ALL") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldBeEqualTo 2
                }
            }

            it("Should get all statuses without filter") {
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                    SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                    SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT)
                )

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldBeEqualTo 2
                }
            }

            it("Should get latest status") {
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT, erAvvist = false, erEgenmeldt = false))

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldBeEqualTo 1
                    responseData[0].statusEvent shouldBeEqualTo StatusEventDTO.SENDT
                    responseData[0].erAvvist shouldBeEqualTo false
                    responseData[0].erEgenmeldt shouldBeEqualTo false
                }
            }

            it("Should get forbidden when not owner") {
                every { sykmeldingStatusService.erEier(any(), any()) } returns false
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = JWTPrincipal(mockPayload)
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                }
            }
        }
    }
})
