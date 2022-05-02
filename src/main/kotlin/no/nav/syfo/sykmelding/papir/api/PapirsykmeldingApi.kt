package no.nav.syfo.sykmelding.papir.api

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.accept
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.log
import no.nav.syfo.sykmelding.papir.PapirsykmeldingService

fun Route.registrerServiceuserPapirsykmeldingApi(papirsykmeldingService: PapirsykmeldingService) {
    route("/papirsykmelding") {
        accept(ContentType.Application.Json) {
            get("/{sykmeldingId}") {
                val sykmeldingId = call.parameters["sykmeldingId"]!!
                val sykmelding = papirsykmeldingService.getPapirsykmelding(sykmeldingId)
                if (sykmelding == null) {
                    log.info("Fant ikke sykmelding med id {}", sykmeldingId)
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(sykmelding)
                }
            }
        }
    }
}
