package no.nav.syfo.rerunkafka.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.rerunkafka.service.RerunKafkaService

data class ResponseDTO(val accepted: List<String>, val notFound: List<String>)

data class RerunRequest(val ids: List<String>, val behandlingsutfall: ValidationResult)

fun Route.registerRerunKafkaApi(rerunKafkaService: RerunKafkaService) {
    route("api/v1/rerun") {
        post {
            val rerunRequest = call.receive<RerunRequest>()
            val accepted = rerunKafkaService.rerun(rerunRequest).map { it.sykmelding.id }
            val notFound = rerunRequest.ids.filter { !accepted.contains(it) }
            call.respond(HttpStatusCode.Accepted, ResponseDTO(accepted, notFound))
        }
    }
}
