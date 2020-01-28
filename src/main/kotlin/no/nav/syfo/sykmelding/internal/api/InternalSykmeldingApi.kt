package no.nav.syfo.sykmelding.internal.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.sykmelding.internal.service.InternalSykmeldingService
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService

fun Route.registrerInternalSykmeldingApi(sykmeldingService: InternalSykmeldingService, tilgangskontrollService: TilgangskontrollService) {
    route("/api/v1/internal") {
        get("/sykmeldinger") {
            val token = call.request.headers["Authentication"]?.removePrefix("Bearer ")
            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val fnr = call.request.queryParameters["fnr"]
                when {
                    fnr.isNullOrEmpty() -> call.respond(HttpStatusCode.BadRequest, "Missing query param: fnr")
                    tilgangskontrollService.hasAccessToUser(fnr, token) -> call.respond(HttpStatusCode.OK, sykmeldingService.hentInternalSykmelding(fnr))
                    else -> call.respond(HttpStatusCode.Forbidden, "Forbidden")
                }
            }
        }
    }
}
