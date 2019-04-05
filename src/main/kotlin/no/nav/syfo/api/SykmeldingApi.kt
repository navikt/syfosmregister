package no.nav.syfo.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.Database
import no.nav.syfo.db.find
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(database: Database) {
    get("/api/v1/sykmeldinger") {
        // TODO: Trace interceptor
        log.info("Incomming request get sykmeldinger")
        val principal: JWTPrincipal? = call.authentication.principal()
        val subject = principal?.payload?.subject
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Request has no subject")
                    return@get
                }

        val sykmeldinger = database.find(subject)

        when {
            sykmeldinger.isNotEmpty() -> call.respond(sykmeldinger)
            else -> call.respond(HttpStatusCode.NoContent)
        }
    }
}
