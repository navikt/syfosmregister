package no.nav.syfo.application

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.internal.api.registrerInternalSykmeldingApi
import no.nav.syfo.sykmelding.internal.api.registrerInternalSykmeldingApiV2
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.status.api.registerSykmeldingStatusGETApi
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.sykmelding.status.registrerSendt
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.getSykmeldingOpplysninger
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument

fun main() {

    val db = TestDB()
    val sykmeldingsopplysning = getSykmeldingOpplysninger("01234567891")
    db.lagreMottattSykmelding(sykmeldingsopplysning, testSykmeldingsdokument.copy(id = "123"))
    db.registerStatus(SykmeldingStatusEvent(sykmeldingsopplysning.id, sykmeldingsopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC), StatusEvent.APEN))
    db.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "123"))
    db.registrerSendt(SykmeldingSendEvent(
            sykmeldingsopplysning.id,
            OffsetDateTime.now(ZoneOffset.UTC),
            ArbeidsgiverStatus(sykmeldingsopplysning.id, "123", "123", "navn"),
            Sporsmal("Arbeidssituajson", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingsopplysning.id, null, Svartype.ARBEIDSSITUASJON, "EN_ARBEIDSGIVER"))),
            SykmeldingStatusEvent(sykmeldingsopplysning.id, OffsetDateTime.now(ZoneOffset.UTC), StatusEvent.SENDT))

    val tilgangskontrollService = mockkClass(TilgangskontrollService::class)
    val sykmeldingStatusService = SykmeldingStatusService(db)
    val sykmeldingerService = SykmeldingerService(database = db)
    val mockPayload = mockk<Payload>()
    coEvery { tilgangskontrollService.hasAccessToUser(any(), any()) } returns true
    coEvery { tilgangskontrollService.hasAccessToUserOboToken(any(), any()) } returns true
    every { mockPayload.subject } returns "01234567891"
    embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        intercept(ApplicationCallPipeline.Call) {
            call.authentication.principal = JWTPrincipal(mockPayload)
        }
        routing {
            registerNaisApi(ApplicationState(true, true))
            registrerInternalSykmeldingApi(sykmeldingerService, tilgangskontrollService)
            registerSykmeldingStatusGETApi(sykmeldingStatusService)
            registrerSykmeldingApiV2(sykmeldingerService)
            registrerInternalSykmeldingApiV2(sykmeldingerService, tilgangskontrollService)
        }
    }.start(true)
}
