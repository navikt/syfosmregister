package no.nav.syfo.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import no.nav.syfo.VaultSecrets
import java.net.URL
import java.util.concurrent.TimeUnit

class JwtConfig(private val vaultSecrets: VaultSecrets) {
    val jwkProvider: JwkProvider = JwkProviderBuilder(URL(vaultSecrets.jwksUri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    fun validate(credentials: JWTCredential): JWTPrincipal? = try {
        requireNotNull(credentials.payload.audience) { "Auth: Missing audience in token" }
        require(credentials.payload.audience.contains(vaultSecrets.jwtAudience)) { "Auth: Valid audience not found in claims" }
        log.debug(
                "Auth: Resource requested by '${credentials.payload.getClaim("name").asString()}' " +
                        "\n NAV ident: '${credentials.payload.getClaim("NAVident").asString()}'" +
                        "\n Unique Name: '${credentials.payload.getClaim("unique_name").asString()}'" +
                        "\n IP address: '${credentials.payload.getClaim("ipaddr").asString()}'" +
                        "\n Groups: '${credentials.payload.getClaim("groups").asArray(String::class.java).joinToString()}'"
        )
        JWTPrincipal(credentials.payload)
    } catch (e: Throwable) {
        log.error("Auth: Token validation failed: ${e.message}", e)
        null
    }

    companion object {
        const val REALM = "Syfosmregister"
    }
}
