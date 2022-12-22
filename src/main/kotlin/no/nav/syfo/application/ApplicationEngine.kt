package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.sykmelding.internal.api.registrerInternalSykmeldingApiV2
import no.nav.syfo.sykmelding.internal.api.setupSwaggerDocApi
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.papir.PapirsykmeldingService
import no.nav.syfo.sykmelding.papir.api.registrerServiceuserPapirsykmeldingApi
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.serviceuser.api.registrerSykmeldingServiceuserApiV2
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import java.util.UUID

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    database: DatabaseInterface,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    jwkProviderAadV2: JwkProvider,
    sykmeldingerService: SykmeldingerService,
    tilgangskontrollService: TilgangskontrollService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }
        setupAuth(
            tokenXIssuer = tokenXIssuer,
            jwkProviderAadV2 = jwkProviderAadV2,
            jwkProviderTokenX = jwkProviderTokenX,
            environment = env
        )
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")

                log.error("Caught exception", cause)
                throw cause
            }
        }

        routing {
            if (env.cluster == "dev-gcp") {
                setupSwaggerDocApi()
            }
            route("internal") {
                registerNaisApi(applicationState)
            }
            route("/api/v2") {
                authenticate("azureadv2") {
                    registrerSykmeldingServiceuserApiV2(sykmeldingerService)
                    registrerInternalSykmeldingApiV2(sykmeldingerService, tilgangskontrollService)
                    registrerServiceuserPapirsykmeldingApi(papirsykmeldingService = PapirsykmeldingService(database))
                }
            }
            route("/api/v3") {
                authenticate("tokenx") {
                    registrerSykmeldingApiV2(sykmeldingerService)
                }
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
