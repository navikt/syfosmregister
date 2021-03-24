package no.nav.syfo.sykmelding.serviceuser.api

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.accept
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import java.time.LocalDate
import no.nav.syfo.log
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.serviceuser.api.model.StatusRequest

fun Route.registrerSykmeldingServiceuserApiV1(sykmeldingerService: SykmeldingerService) {
    route("api/v1/sykmelding") {
        accept(ContentType.Application.Json) {
            get("/{sykmeldingId}") {
                val sykmeldingId = call.parameters["sykmeldingId"]!!
                val sykmelding = sykmeldingerService.getSykmeldingMedId(sykmeldingId)
                if (sykmelding == null) {
                    log.info("Fant ikke sykmelding med id {}", sykmeldingId)
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(sykmelding)
                }
            }
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
            post("/sykmeldtStatus") {
                val statusRequest = call.receive<StatusRequest>()
                call.respond(HttpStatusCode.OK, sykmeldingerService.getSykmeldtStatusForDato(statusRequest.fnr, statusRequest.dato))
            }
        }
    }
}
