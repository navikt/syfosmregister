package no.nav.syfo.sykmelding.internal.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.log
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.util.getFnrFromHeader
import java.time.LocalDate

fun Route.registrerInternalSykmeldingApiV2(
    sykmeldingService: SykmeldingerService,
    tilgangskontrollService: TilgangskontrollService,
) {
    route("/internal") {
        get("/sykmeldinger") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            log.info("Got request to V2 endpoint")
            try {
                if (token == null) {
                    log.warn("Token is missing, returing with httpstatus Unauthorized")
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val fnr = getFnrFromHeader()
                    val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                    val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }

                    when {
                        fnr.isNullOrEmpty() -> call.respond(HttpStatusCode.BadRequest, "Missing header: fnr")
                        tilgangskontrollService.hasAccessToUserOboToken(fnr, token) -> call.respond(
                            HttpStatusCode.OK,
                            sykmeldingService.getInternalSykmeldinger(fnr, fom, tom),
                        )
                        else -> call.respond(HttpStatusCode.Forbidden, "Forbidden")
                    }
                }
            } catch (ex: Exception) {
                log.error("Error in v2 endpoint", ex)
                call.respond(HttpStatusCode.InternalServerError, "unknown error")
            }
        }
    }
}
