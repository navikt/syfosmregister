package no.nav.syfo.sykmeldingstatus.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.aksessering.api.log
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import org.postgresql.util.PSQLException

fun Route.registerSykmeldingStatusApi(sykmeldingStatusService: SykmeldingStatusService) {

    post("/sykmeldinger/{sykmeldingsid}/status") {
        val sykmeldingId = call.parameters["sykmeldingsid"]!!
        val sykmeldingStatusEventDTO = call.receive<SykmeldingStatusEventDTO>()
        val sykmeldingStatusEvent = SykmeldingStatusEvent(
                sykmeldingId,
                sykmeldingStatusEventDTO.timestamp,
                sykmeldingStatusEventDTO.statusEvent.toStatusEvent())
        try {
            sykmeldingStatusService.registrerStatus(sykmeldingStatusEvent)
            call.respond(HttpStatusCode.Created)
        } catch (ex: PSQLException) {
            if (ex.serverErrorMessage.message.contains("duplicate key")) {
                log.info("Conflict", ex)
                call.respond(HttpStatusCode.Conflict)
            } else {
                log.error("Internal server error", ex)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
