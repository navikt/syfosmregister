package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.nullstilling.registerNullstillApi
import no.nav.syfo.sykmelding.internal.api.registrerInternalSykmeldingApiV2
import no.nav.syfo.sykmelding.internal.api.setupSwaggerDocApi
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.serviceuser.api.registrerSykmeldingServiceuserApiV2
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.sykmelding.status.api.registerSykmeldingStatusGETApi
import no.nav.syfo.sykmelding.user.api.registrerSykmeldingApiV2
import java.util.UUID

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    database: DatabaseInterface,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    cluster: String,
    sykmeldingStatusService: SykmeldingStatusService,
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
            loginserviceIdportenAudience = env.loginserviceIdportenAudience,
            vaultSecrets = vaultSecrets,
            jwkProvider = jwkProvider,
            issuer = issuer,
            jwkProviderAadV2 = jwkProviderAadV2,
            environment = env
        )
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")

                log.error("Caught exception", cause)
                throw cause
            }
        }

        routing {

            if (env.cluster == "dev-fss") {
                setupSwaggerDocApi()
            }
            registerNaisApi(applicationState)
            authenticate("jwt") {
                registerSykmeldingStatusGETApi(sykmeldingStatusService)
                registrerSykmeldingApiV2(sykmeldingerService)
            }
            authenticate("azureadv2") {
                registrerSykmeldingServiceuserApiV2(sykmeldingerService)
                registrerInternalSykmeldingApiV2(sykmeldingerService, tilgangskontrollService)
            }
            authenticate("basic") {
                registerNullstillApi(database, cluster)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
