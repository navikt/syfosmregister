package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.basic
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

fun Application.setupAuth(vaultSecrets: VaultSecrets, jwkProvider: JwkProvider) {
    install(Authentication) {
        jwt(name = "jwt") {
            verifier(jwkProvider, vaultSecrets.oidcWellKnownUri)
            validate { credentials ->
                if (!credentials.payload.audience.contains(vaultSecrets.loginserviceClientId)) {
                    log.warn(
                        "Auth: Unexpected audience for jwt {}, {}",
                        StructuredArguments.keyValue("issuer", credentials.payload.issuer),
                        StructuredArguments.keyValue("audience", credentials.payload.audience)
                    )
                    null
                } else {
                    JWTPrincipal(credentials.payload)
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
