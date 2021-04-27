package no.nav.syfo.nullstilling

import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log

@KtorExperimentalAPI
fun Route.registerNullstillApi(database: DatabaseInterface, clusterName: String) {
    if (clusterName == "dev-fss") {
        route("/internal") {
            delete("/nullstillSykmeldinger/{aktorId}") {
                val aktorId = call.parameters["aktorId"] ?: throw RuntimeException("Aktorid mangler i requesten")

                log.info("Nullstiller sykmeldinger på aktor: {}", aktorId)
                database.nullstillSykmeldinger(aktorId)
                call.respondText("ok!")
            }
        }
    }
}
