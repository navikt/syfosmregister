package no.nav.syfo.api

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
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.finnBrukersSykmeldinger
import no.nav.syfo.db.isSykmeldingOwner
import no.nav.syfo.db.registerLestAvBruker
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(database: DatabaseInterface) {
    route("/api/v1") {

        get("/sykmeldinger") {
            log.info("Incomming request get sykmeldinger")
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            val sykmeldinger = database.finnBrukersSykmeldinger(subject)

            when {
                sykmeldinger.isNotEmpty() -> call.respond(sykmeldinger)
                else -> call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/sykmeldinger/{sykmeldingsid}/bekreft") {
            val sykmeldingsid = call.parameters["sykmeldingsid"]!!
            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            log.info("Incomming request post settLestAvBruker for $sykmeldingsid")

            if (database.isSykmeldingOwner(sykmeldingsid, subject)) {
                if (database.registerLestAvBruker(sykmeldingsid) > 0) {
                    call.respond(HttpStatusCode.OK)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
