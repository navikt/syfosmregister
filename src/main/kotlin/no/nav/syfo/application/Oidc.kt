package no.nav.syfo.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import no.nav.syfo.util.handleResponseException
import no.nav.syfo.util.setupJacksonSerialization
import no.nav.syfo.util.setupRetry

val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    setupJacksonSerialization()
    handleResponseException()
    setupRetry()
}

fun getWellKnownTokenX(wellKnownUrl: String) = runBlocking {
    HttpClient(Apache, config).get(wellKnownUrl).body<WellKnownTokenX>()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)
