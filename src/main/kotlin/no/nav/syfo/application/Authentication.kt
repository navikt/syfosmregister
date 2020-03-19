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
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

fun Application.setupAuth(
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    jwkProviderInternal: JwkProvider,
    jwkProviderServiceuser: JwkProvider,
    issuerServiceuser: String,
    clientId: String,
    appIds: List<String>
) {
    install(Authentication) {
        jwt(name = "internal") {
            verifier(jwkProviderInternal, vaultSecrets.internalJwtIssuer)
            validate { credentials ->
                when {
                    hasInternalLoginServiceClientIdAudience(credentials, vaultSecrets) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "jwt") {
            verifier(jwkProvider, issuer)
            validate { credentials ->
                when {
                    hasLoginserviceClientIdAudience(credentials, vaultSecrets) -> JWTPrincipal(credentials.payload)
                    else -> unauthorized(credentials)
                }
            }
        }
        jwt(name = "jwtserviceuser") {
            verifier(jwkProviderInternal, issuerServiceuser)
            validate { credentials ->
                val appId: String = credentials.payload.getClaim("appid").asString()
                if (appId in appIds && clientId in credentials.payload.audience) {
                    JWTPrincipal(credentials.payload)
                } else {
                    unauthorized(credentials)
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

fun unauthorized(credentials: JWTCredential): Principal? {
    log.warn(
            "Auth: Unexpected audience for jwt {}, {}",
            StructuredArguments.keyValue("issuer", credentials.payload.issuer),
            StructuredArguments.keyValue("audience", credentials.payload.audience)
    )
    return null
}

fun hasLoginserviceClientIdAudience(credentials: JWTCredential, vaultSecrets: VaultSecrets): Boolean {
    return credentials.payload.audience.contains(vaultSecrets.loginserviceClientId)
}

fun hasInternalLoginServiceClientIdAudience(credentials: JWTCredential, vaultSecrets: VaultSecrets): Boolean {
    return credentials.payload.audience.contains(vaultSecrets.internalLoginServiceClientId)
}
