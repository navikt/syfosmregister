package no.nav.syfo.sykmelding.serviceuser.api

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.accept
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.log
import no.nav.syfo.sykmelding.service.SykmeldingerService
import java.time.LocalDate

fun Route.registrerSykmeldingServiceuserApiV1_getAllByFnr(sykmeldingerService: SykmeldingerService) {
    route("api/v1/sykmelding") {
        accept(ContentType.Application.Json) {
            get("/sykmeldinger") {
                val fnr = call.request.headers["fnr"]
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }

                if (fnr.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing header: fnr")
                } else {
                    call.respond(HttpStatusCode.OK, sykmeldingerService.getInternalSykmeldinger(fnr, fom, tom))
                }
            }
        }
    }
}
