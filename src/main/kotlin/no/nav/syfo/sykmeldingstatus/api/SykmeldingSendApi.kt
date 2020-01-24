package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.aksessering.api.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.kafka.model.toSykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer

fun Route.registerSykmeldingSendApi(sykmeldingStatusService: SykmeldingStatusService, sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {

    post("/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val sykmeldingSendEventDTO = call.receive<SykmeldingSendEventDTO>()

        try {
            sykmeldingStatusService.registrerSendt(tilSykmeldingSendEvent(sykmeldingId, sykmeldingSendEventDTO))
            log.info("Sendt sykmelding {}", sykmeldingId)
            sykmeldingStatusKafkaProducer.send(sykmeldingSendEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
            call.respond(HttpStatusCode.Created)
        } catch (ex: Exception) {
            log.error("Noe gikk galt ved innsending av sykmelding {}", sykmeldingId, ex)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
