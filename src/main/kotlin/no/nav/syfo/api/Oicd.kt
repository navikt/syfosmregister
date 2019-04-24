package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache, proxyConfig).get<WellKnown>(wellKnownUrl) }

data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
