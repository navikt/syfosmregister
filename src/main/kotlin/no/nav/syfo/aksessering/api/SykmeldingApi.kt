package no.nav.syfo.aksessering.api

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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingStatusKafkaProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smregister")

@KtorExperimentalAPI
fun Route.registerSykmeldingApi(sykmeldingService: SykmeldingService, sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {
    route("/api/v1") {
        get("/sykmeldinger") {
            val principal: JWTPrincipal = call.authentication.principal()!!
            val fnr = principal.payload.subject
            call.respond(sykmeldingService.hentSykmeldinger(fnr))
        }

        post("/sykmeldinger/{sykmeldingsid}/bekreft") {
            val sykmeldingsid = call.parameters["sykmeldingsid"]!!
            val principal: JWTPrincipal = call.authentication.principal()!!
            val fnr = principal.payload.subject
            if (sykmeldingService.erEier(sykmeldingsid, fnr)) {
                val statusEvent = SykmeldingStatusKafkaEventDTO(
                        sykmeldingId = sykmeldingsid,
                        statusEvent = StatusEventDTO.BEKREFTET,
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                )
                sykmeldingStatusKafkaProducer.send(statusEvent, fnr)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
