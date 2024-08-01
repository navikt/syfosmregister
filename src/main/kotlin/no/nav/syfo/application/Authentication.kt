package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.header
import io.ktor.server.request.path
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.log
import no.nav.syfo.metrics.APP_ID_PATH_COUNTER
import no.nav.syfo.metrics.getLabel

fun Application.setupAuth(
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
    jwkProviderAadV2: JwkProvider,
    environment: Environment,
) {
    install(Authentication) {
        jwt(name = "azureadv2") {
            verifier(jwkProviderAadV2, environment.jwtIssuerV2)
            validate { credentials ->
                when {
                    harTilgang(credentials, environment.clientIdV2) -> {
                        val appid: String = credentials.payload.getClaim("azp").asString()
                        val app = environment.preAuthorizedApp.firstOrNull { it.clientId == appid }
                        if (app != null) {
                            APP_ID_PATH_COUNTER.labels(
                                    app.team,
                                    app.appName,
                                    getLabel(this.request.path()),
                                )
                                .inc()
                        } else {
                            log.warn("App not in pre authorized list: $appid")
                        }
                        JWTPrincipal(credentials.payload)
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "tokenx") {
            authHeader {
                val token: String =
                    it.request.header("Authorization")?.removePrefix("Bearer ")
                        ?: return@authHeader null

                return@authHeader HttpAuthHeader.Single("Bearer", token)
            }
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(credentials, environment.clientIdTokenX) &&
                        erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal,
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun harTilgang(credentials: JWTCredential, clientId: String): Boolean {
    val appid: String = credentials.payload.getClaim("azp").asString()
    log.debug("authorization attempt for $appid")
    return credentials.payload.audience.contains(clientId)
}

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
        "Auth: Unexpected audience for jwt {}, {}",
        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
        StructuredArguments.keyValue("audience", credentials.payload.audience),
    )
    return null
}

fun hasClientIdAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (
        principal.payload.getClaim("pid") != null &&
            !principal.payload.getClaim("pid").asString().isNullOrEmpty()
    ) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal,
) : Principal
