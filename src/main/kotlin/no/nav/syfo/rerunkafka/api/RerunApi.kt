package no.nav.syfo.rerunkafka.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.syfo.rerunkafka.service.RerunKafkaService

fun Route.registerRerunKafkaApi(rerunKafkaService: RerunKafkaService) {
    route("api/v1/rerun") {
        post {
            val ids = call.receive<List<String>>()
            rerunKafkaService.rerun(ids)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
