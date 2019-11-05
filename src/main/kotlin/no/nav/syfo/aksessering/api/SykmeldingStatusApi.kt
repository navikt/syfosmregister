package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.persistering.StatusEvent
import no.nav.syfo.persistering.SykmeldingStatusEvent
import org.postgresql.util.PSQLException

fun Route.registerSykmeldingStatusApi(sykmeldingService: SykmeldingService) {

    post("/sykmeldinger/{sykmeldingsid}/status") {
        val sykmeldingId = call.parameters["sykmeldingsid"]!!
        val sykmeldingStatusEventDTO = call.receive<SykmeldingStatusEventDTO>()
        val sykmeldingStatusEvent = SykmeldingStatusEvent(
                sykmeldingId,
                sykmeldingStatusEventDTO.timestamp,
                sykmeldingStatusEventDTO.statusEvent.toStatusEvent())
        try{
            sykmeldingService.registrerStatus(sykmeldingStatusEvent)
            call.respond(HttpStatusCode.Created)
        } catch (ex: PSQLException) {
            if(ex.serverErrorMessage.message.contains("duplicate key")) {
                call.respond(HttpStatusCode.Conflict)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

private fun StatusEventDTO.toStatusEvent(): StatusEvent {
    return when (this) {
        StatusEventDTO.CONFIRMED -> StatusEvent.CONFIRMED
        StatusEventDTO.OPEN -> StatusEvent.OPEN
        StatusEventDTO.SENT -> StatusEvent.SENT
        StatusEventDTO.CANCELED -> StatusEvent.CANCELED
        StatusEventDTO.EXPIRED -> StatusEvent.EXPIRED
    }
}
