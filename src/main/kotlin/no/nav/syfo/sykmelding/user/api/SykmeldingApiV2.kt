package no.nav.syfo.sykmelding.user

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.sykmelding.service.SykmeldingerService

fun Route.registrerSykmeldingApiV2(sykmeldingerService: SykmeldingerService) {
    route("api/v2/sykmeldinger") {
        get {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val fnr = principal.payload.subject
            call.respond(sykmeldingerService.getUserSykmelding(fnr))
        }
    }
}
