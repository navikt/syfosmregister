package no.nav.syfo.aksessering.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.aksessering.tilgang.TilgangskontrollService
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.getInternalSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class InternalSykmeldingApiKtTest : Spek({
    val uri = "/api/v1/internal/sykmeldinger"

    val sykmeldingService = mockkClass(SykmeldingService::class)
    val tilgangskontrollService = mockkClass(TilgangskontrollService::class)
    coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns true
    describe("Test internal sykmelding api") {
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
                registrerInternalSykmeldingApi(sykmeldingService, tilgangskontrollService)
            }

            it("Skal returnere tom liste") {
                every { sykmeldingService.hentInternalSykmelding(any()) } returns emptyList()
                with(handleRequest(HttpMethod.Get, "$uri?fnr=12345678901", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SkjermetSykmeldingDTO>>(response.content!!) shouldEqual emptyList()
                }
            }

            it("Skal returnere liste med sykmeldinger") {
                val sykmeldingList = listOf(getInternalSykmelding())
                every { sykmeldingService.hentInternalSykmelding(any()) } returns sykmeldingList
                with(handleRequest(HttpMethod.Get, "$uri?fnr=1234567891", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual objectMapper.writeValueAsString(sykmeldingList)
                }
            }

            it("Skal returnere bad request when fnr is missing") {
                with(handleRequest(HttpMethod.Get, uri, setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                    response.content shouldEqual "Missing query param: fnr"
                }
            }

            it("Should get empty list when user does not have access to person") {
                coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns false
                every { sykmeldingService.hentInternalSykmelding(any()) } returns listOf(getInternalSykmelding())
                with(handleRequest(HttpMethod.Get, "$uri?fnr=12345678901", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SkjermetSykmeldingDTO>>(response.content!!) shouldEqual emptyList()
                }
            }
        }
    }
})

private fun setUPHeaders(): TestApplicationRequest.() -> Unit {
    return {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        addHeader("Authentication", "Bearer 123")
    }
}
