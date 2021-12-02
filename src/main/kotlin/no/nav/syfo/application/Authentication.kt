package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.Principal
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.basic
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

fun Application.setupAuth(
    loginserviceIdportenAudience: List<String>,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    jwkProviderInternal: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>,
    jwkProviderAadV2: JwkProvider,
    environment: Environment
) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials ->
                when {
                    hasLoginserviceIdportenClientIdAudience(credentials, loginserviceIdportenAudience) && erNiva4(credentials) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "jwtserviceuser") {
            verifier(jwkProviderInternal, issuerServiceuser)
            validate { credentials ->
                val appId: String = credentials.payload.getClaim("azp").asString()
                if (appId in appIds && clientId in credentials.payload.audience) {
                    JWTPrincipal(credentials.payload)
                } else {
                    unauthorized(credentials)
                }
            }
        }
        jwt(name = "azureadv2") {
            verifier(jwkProviderAadV2, environment.jwtIssuerV2)
            validate { credentials ->
                when {
                    harTilgang(credentials, environment.clientIdV2) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
        basic(name = "basic") {
            validate { credentials ->
                if (credentials.name == vaultSecrets.syfomockUsername && credentials.password == vaultSecrets.syfomockPassword) {
                    UserIdPrincipal(credentials.name)
                } else null
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
        StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasLoginserviceIdportenClientIdAudience(credentials: JWTCredential, loginserviceIdportenClientId: List<String>): Boolean {
    return loginserviceIdportenClientId.any { credentials.payload.audience.contains(it) }
}

fun erNiva4(credentials: JWTCredential): Boolean {
    return "Level4" == credentials.payload.getClaim("acr").asString()
}
