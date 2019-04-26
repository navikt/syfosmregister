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
import no.nav.syfo.model.ValidationResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(database: Database) {

    get("/api/v1/behandlingsutfall") {
        // TODO: Trace interceptor
        log.info("Incomming request get behandlingsutfall")
        val principal: JWTPrincipal = call.authentication.principal()!!
        val subject = principal.payload.subject

        val behandlingsutfall = database.find(subject)
            // TODO: Should behandlingsUtfall be hidden for patients with skjermetForPasient
            .filter { !it.sykmelding.skjermesForPasient }
            .map { BehandlingsutfallResponse(it.id, it.behandlingsUtfall) }

        when {
            behandlingsutfall.isNotEmpty() -> call.respond(behandlingsutfall)
            else -> call.respond(HttpStatusCode.NoContent)
        }
    }
}

data class BehandlingsutfallResponse(val id: String, val behandlingsutfall: ValidationResult)
