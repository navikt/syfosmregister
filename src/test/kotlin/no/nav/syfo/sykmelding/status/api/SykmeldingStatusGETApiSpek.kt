package no.nav.syfo.sykmelding.status.api

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.StatusEventDTO
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.status.api.model.SykmeldingStatusEventDTO
import no.nav.syfo.testutil.setUpContentNegotiation
import org.amshove.kluent.shouldBeEqualTo
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusGETApiSpek : FunSpec({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val mockPayload = mockk<Payload>()

    beforeTest {
        clearAllMocks()
        every { mockPayload.subject } returns "pasient_fnr"
        every { sykmeldingStatusService.erEier(any(), any()) } returns true
    }

    context("Get SykmeldingStatus") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation, setUpContentNegotiation())
            application.routing { registerSykmeldingStatusGETApi(sykmeldingStatusService) }

            test("Should get all statuses sykmeldingstatus") {
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                    SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                    SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT)
                )

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=ALL") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = BrukerPrincipal("pasient_fnr", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldBeEqualTo 2
                }
            }

            test("Should get all statuses without filter") {
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(
                    SykmeldingStatusEvent("123", timestamp, StatusEvent.APEN),
                    SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT)
                )

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = BrukerPrincipal("pasient_fnr", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val responseData = objectMapper.readValue<List<SykmeldingStatusEventDTO>>(response.content!!)
                    responseData.size shouldBeEqualTo 2
                }
            }

            test("Should get latest status") {
                val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                every { sykmeldingStatusService.getSykmeldingStatus(any(), any()) } returns listOf(SykmeldingStatusEvent("123", timestamp.plusSeconds(10), StatusEvent.SENDT, erAvvist = false, erEgenmeldt = false))

                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = BrukerPrincipal("pasient_fnr", JWTPrincipal(mockPayload))
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

            test("Should get forbidden when not owner") {
                every { sykmeldingStatusService.erEier(any(), any()) } returns false
                with(
                    handleRequest(HttpMethod.Get, "/sykmeldinger/123/status?filter=LATEST") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        call.authentication.principal = BrukerPrincipal("pasient_fnr", JWTPrincipal(mockPayload))
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                }
            }
        }
    }
})
