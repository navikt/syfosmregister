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
import no.nav.syfo.aksessering.db.erEier
import no.nav.syfo.aksessering.db.hentSykmeldinger
import no.nav.syfo.aksessering.db.registrerLestAvBruker
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.toDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(database: DatabaseInterface) {
    route("/api/v1") {

        get("/sykmeldinger") {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            val sykmeldinger: List<SykmeldingDTO> =
                database.hentSykmeldinger(subject).map { it.toDTO() }

            when {
                sykmeldinger.isNotEmpty() -> call.respond(sykmeldinger)
                else -> call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/sykmeldinger/{sykmeldingsid}/bekreft") {
            val sykmeldingsid = call.parameters["sykmeldingsid"]!!
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            if (database.erEier(sykmeldingsid, subject)) {
                if (database.registrerLestAvBruker(sykmeldingsid) > 0) {
                    call.respond(HttpStatusCode.OK)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
