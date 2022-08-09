package no.nav.syfo.sykmelding.user.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.accept
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.log
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.StatusEventDTO
import java.time.LocalDate

fun Route.registrerSykmeldingApiV2(sykmeldingerService: SykmeldingerService) {
    route("/sykmeldinger") {
        accept(ContentType.Application.Json) {
            get {
                val principal: BrukerPrincipal = call.authentication.principal()!!
                val fnr = principal.fnr
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }
                val exclude = call.parameters.getAll("exclude")
                val include = call.parameters.getAll("include")

                when {
                    checkExcludeInclude(exclude, include) -> call.respond(HttpStatusCode.BadRequest, "Can not use both include and exclude")
                    checkFomAndTomDate(fom, tom) -> call.respond(HttpStatusCode.BadRequest, "FOM should be before or equal to TOM")
                    hasInvalidStatus(exclude ?: include) -> call.respond(HttpStatusCode.BadRequest, "include or exclude can only contain ${StatusEventDTO.values().joinToString()}")
                    else -> call.respond(sykmeldingerService.getUserSykmelding(fnr, fom, tom, include, exclude, fullBehandler = false))
                }
            }
            get("/{sykmeldingId}") {
                val principal: BrukerPrincipal = call.authentication.principal()!!
                val fnr = principal.fnr
                val sykmeldingId = call.parameters["sykmeldingId"]!!

                val sykmelding = sykmeldingerService.getSykmelding(sykmeldingId, fnr, fullBehandler = false)

                when (sykmelding) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(sykmelding)
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
