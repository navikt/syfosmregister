package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService) {
    route("/api/v1") {

        get("/sykmeldinger") {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            val sykmeldinger: List<SykmeldingDTO> = sykmeldingService.hentSykmeldinger(subject)

            when {
                sykmeldinger.isNotEmpty() -> call.respond(sykmeldinger)
                else -> call.respond(emptyList<SykmeldingDTO>())
            }
        }

        post("/sykmeldinger/{sykmeldingsid}/bekreft") {
            val sykmeldingsid = call.parameters["sykmeldingsid"]!!
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject
            if (sykmeldingService.erEier(sykmeldingsid, subject)) {
                sykmeldingService.registrerStatus(SykmeldingStatusEvent(sykmeldingsid, LocalDateTime.now(), StatusEvent.CONFIRMED))
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
