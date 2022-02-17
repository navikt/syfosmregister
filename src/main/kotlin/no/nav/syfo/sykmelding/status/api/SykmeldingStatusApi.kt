package no.nav.syfo.sykmelding.status.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.status.api.model.SykmeldingStatusApiModelMapper

fun Route.registerSykmeldingStatusGETApi(sykmeldingStatusService: SykmeldingStatusService) {
    get("/sykmeldinger/{sykmeldingId}/status") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val filter = call.request.queryParameters["filter"]
        when (sykmeldingStatusService.erEier(sykmeldingId, fnr)) {
            true -> call.respond(SykmeldingStatusApiModelMapper.toSykmeldingStatusList(sykmeldingStatusService.getSykmeldingStatus(sykmeldingId, filter)))
            else -> call.respond(HttpStatusCode.Forbidden)
        }
    }
}
