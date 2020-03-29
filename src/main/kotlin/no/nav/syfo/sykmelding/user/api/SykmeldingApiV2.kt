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
import no.nav.syfo.sykmeldingstatus.StatusEventDTO

fun Route.registrerSykmeldingApiV2(sykmeldingerService: SykmeldingerService) {
    route("api/v2/sykmeldinger") {
        accept(ContentType.Application.Json) {
            get {
                val principal: JWTPrincipal = call.authentication.principal()!!
                val fnr = principal.payload.subject
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }
                val exclude = call.parameters.getAll("exclude")
                val include = call.parameters.getAll("include")

                when {
                    checkExcludeInclude(exclude, include) -> call.respond(HttpStatusCode.BadRequest, "Can not use both include and exclude")
                    checkFomAndTomDate(fom, tom) -> call.respond(HttpStatusCode.BadRequest, "FOM should be before or equal to TOM")
                    hasInvalidStatus(exclude ?: include) -> call.respond(HttpStatusCode.BadRequest, "include or exclude can only contain ${StatusEventDTO.values().joinToString()}")
                    else -> call.respond(sykmeldingerService.getUserSykmelding(fnr, fom, tom, include, exclude))
                }
            }
        }
    }
}

private fun hasInvalidStatus(statusFilter: List<String>?): Boolean {
    return !statusFilter.isNullOrEmpty() && !StatusEventDTO.values().map { it.name }.containsAll(statusFilter)
}

private fun checkExcludeInclude(exclude: List<String>?, include: List<String>?): Boolean {
    return exclude != null && include != null
}

private fun checkFomAndTomDate(fom: LocalDate?, tom: LocalDate?) =
        fom != null && tom != null && tom.isBefore(fom)
