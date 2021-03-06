package no.nav.syfo.sykmelding.internal.api

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
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.testutil.getSykmeldingDto
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class InternalSykmeldingApiKtTest : Spek({
    val uri = "/api/v1/internal/sykmeldinger"

    val sykmeldingService = mockkClass(SykmeldingerService::class)
    val tilgangskontrollService = mockkClass(TilgangskontrollService::class)
    beforeEachTest {
        coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns true
    }
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
                every { sykmeldingService.getInternalSykmeldinger(any()) } returns emptyList()
                with(handleRequest(HttpMethod.Get, "$uri", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!) shouldEqual emptyList()
                }
            }

            it("Skal returnere liste med sykmeldinger") {
                val sykmeldingList = listOf(getSykmeldingDto())
                every { sykmeldingService.getInternalSykmeldinger(any()) } returns sykmeldingList
                with(handleRequest(HttpMethod.Get, "$uri", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.content shouldEqual objectMapper.writeValueAsString(sykmeldingList)
                }
            }

            it("Skal returnere bad request nar fodselsnummer ikke er tilgjengelig") {
                with(handleRequest(HttpMethod.Get, uri) {
                    addHeader("Authorization", "Bearer 123")
                }) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                    response.content shouldEqual "Missing header: fnr"
                }
            }

            it("Should get Forbidden when user does not have access to person") {
                coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns false
                every { sykmeldingService.getInternalSykmeldinger(any()) } returns emptyList()
                with(handleRequest(HttpMethod.Get, "$uri", setUPHeaders())) {
                    response.status() shouldEqual HttpStatusCode.Forbidden
                }
            }
        }
    }
})

private fun setUPHeaders(): TestApplicationRequest.() -> Unit {
    return {
        addHeader("Authorization", "Bearer 123")
        addHeader("fnr", "01234567891")
    }
}
