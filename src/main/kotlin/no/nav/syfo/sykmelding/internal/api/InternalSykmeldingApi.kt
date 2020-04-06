package no.nav.syfo.sykmelding.internal.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import java.time.LocalDate
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.service.SykmeldingerService

fun Route.registrerInternalSykmeldingApi(sykmeldingService: SykmeldingerService, tilgangskontrollService: TilgangskontrollService) {
    route("/api/v1/internal") {
        get("/sykmeldinger") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")

            if (token == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val fnr = call.request.headers["fnr"]
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }

                when {
                    fnr.isNullOrEmpty() -> call.respond(HttpStatusCode.BadRequest, "Missing header: fnr")
                    tilgangskontrollService.hasAccessToUser(fnr, token) -> call.respond(HttpStatusCode.OK, sykmeldingService.getInternalSykmeldinger(fnr, fom, tom))
                    else -> call.respond(HttpStatusCode.Forbidden, "Forbidden")
                }
            }
        }
    }
}
