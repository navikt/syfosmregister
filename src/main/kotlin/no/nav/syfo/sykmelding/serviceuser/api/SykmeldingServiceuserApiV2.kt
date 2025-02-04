package no.nav.syfo.sykmelding.serviceuser.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.accept
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.LocalDate
import no.nav.syfo.log
import no.nav.syfo.securelog
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.serviceuser.api.model.StatusRequest
import no.nav.syfo.util.getFnrFromHeader

fun Route.registrerSykmeldingServiceuserApiV2(sykmeldingerService: SykmeldingerService) {
    route("/sykmelding") {
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
            get("/sykinn/{sykmeldingId}") {
                val sykmeldingId = call.parameters["sykmeldingId"]!!
                val sykmelding = sykmeldingerService.getSykInnSykmeldingMedId(sykmeldingId)
                if (sykmelding == null) {
                    log.info("Fant ikke sykmelding med id {}", sykmeldingId)
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(sykmelding)
                }
            }
            get("/sykinn/ident") {
                val ident = call.request.headers["X-IDENT"]!!
                val sykmeldinger = sykmeldingerService.getSykInnSykmeldingForIdent(ident)
                if (sykmeldinger.isEmpty()) {
                    securelog.info("Fant ikke sykmelding for ident {}", ident)
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(sykmeldinger)
                }
            }


            get("/sykmeldinger") {
                val fnr = getFnrFromHeader()
                val fom = call.parameters["fom"]?.let { LocalDate.parse(it) }
                val tom = call.parameters["tom"]?.let { LocalDate.parse(it) }

                if (fnr.isNullOrEmpty()) {
                    log.warn("Missing header: fnr")
                    call.respond(HttpStatusCode.BadRequest, "Missing header: fnr")
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        sykmeldingerService.getInternalSykmeldinger(fnr, fom, tom)
                    )
                }
            }
            post("/sykmeldtStatus") {
                val statusRequest = call.receive<StatusRequest>()
                call.respond(
                    HttpStatusCode.OK,
                    sykmeldingerService.getSykmeldtStatusForDato(
                        statusRequest.fnr,
                        statusRequest.dato
                    )
                )
            }
        }
    }
}
