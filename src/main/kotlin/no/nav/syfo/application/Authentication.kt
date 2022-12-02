package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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
    loginserviceIdportenAudience: List<String>,
    jwkProvider: JwkProvider,
    jwkProviderTokenX: JwkProvider,
    issuer: String,
    tokenXIssuer: String,
    jwkProviderAadV2: JwkProvider,
    environment: Environment
) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, loginserviceIdportenAudience) && erNiva4(credentials) -> {
                        val principal = JWTPrincipal(credentials.payload)
                        BrukerPrincipal(
                            fnr = finnFnrFraToken(principal),
                            principal = principal
                        )
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "azureadv2") {
            verifier(jwkProviderAadV2, environment.jwtIssuerV2)
            validate { credentials ->
                when {
                    harTilgang(credentials, environment.clientIdV2) -> {
                        val appid: String = credentials.payload.getClaim("azp").asString()
                        APP_ID_PATH_COUNTER.labels(appid, getLabel(request.path())).inc()
                        JWTPrincipal(credentials.payload)
                    }
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "tokenx") {
            authHeader {
                if (it.getToken() == null) {
                    return@authHeader null
                }
                return@authHeader HttpAuthHeader.Single("Bearer", it.getToken()!!)
            }
            verifier(jwkProviderTokenX, tokenXIssuer)
            validate { credentials ->
                when {
                    hasClientIdAudience(credentials, environment.clientIdTokenX) && erNiva4(credentials) ->
                        {
                            val principal = JWTPrincipal(credentials.payload)
                            BrukerPrincipal(
                                fnr = finnFnrFraToken(principal),
                                principal = principal
                            )
                        }
                    else -> unauthorized(credentials)
                }
            }
        }
    }
}

fun ApplicationCall.getToken(): String? {
    if (request.header("Authorization") != null) {
        return request.header("Authorization")!!.removePrefix("Bearer ")
    }
    return request.cookies.get(name = "selvbetjening-idtoken")
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
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasClientIdAudience(credentials: JWTCredential, clientId: String): Boolean {
    return credentials.payload.audience.contains(clientId)
}

fun hasLoginserviceIdportenClientIdAudience(credentials: JWTCredential, loginserviceIdportenClientId: List<String>): Boolean {
    return loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}

fun finnFnrFraToken(principal: JWTPrincipal): String {
    return if (principal.payload.getClaim("pid") != null && !principal.payload.getClaim("pid").asString().isNullOrEmpty()) {
        log.debug("Bruker fnr fra pid-claim")
        principal.payload.getClaim("pid").asString()
    } else {
        log.debug("Bruker fnr fra subject")
        principal.payload.subject
    }
}

data class BrukerPrincipal(
    val fnr: String,
    val principal: JWTPrincipal
) : Principal
