package no.nav.syfo.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.Database
import no.nav.syfo.db.find
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Routing.registerSykmeldingApi(database: Database) {
    get("/api/v1/{aktorid}/sykmeldinger") {
        log.info("Incomming request get sykmeldinger")
        val aktorid = call.receiveText()
        val sykmeldinger = database.find(aktorid)

        when (sykmeldinger.isNotEmpty()) {
            true -> call.respond(sykmeldinger)
            else -> call.respond(HttpStatusCode.NoContent)
        }
    }
}
