package no.nav.syfo.sykmelding.user.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.accept
import io.ktor.routing.get
import io.ktor.routing.route
import java.time.LocalDate
import no.nav.syfo.sykmelding.service.SykmeldingerService

fun Route.registrerSykmeldingApiV2(sykmeldingerService: SykmeldingerService) {
    route("api/v2/sykmeldinger") {
        accept(ContentType.Application.Json) {
            get {
                val principal: JWTPrincipal = call.authentication.principal()!!
                val fnr = principal.payload.subject
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }

                when (hasBadQueryparams(fom, tom)) {
                    true -> call.respond(HttpStatusCode.BadRequest, "FOM should be before or equal to TOM")
                    else -> call.respond(sykmeldingerService.getUserSykmelding(fnr, fom, tom))
                }
            }
        }
    }
}

private fun hasBadQueryparams(fom: LocalDate?, tom: LocalDate?) =
        fom != null && tom != null && tom.isBefore(fom)
