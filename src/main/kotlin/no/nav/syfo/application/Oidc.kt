package no.nav.syfo.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.log

val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            when (exception) {
                is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
            }
        }
    }
    install(HttpRequestRetry) {
        constantDelay(100, 0, false)
        retryOnExceptionIf(3) { request, throwable ->
            log.warn("Caught exception in oidc ${throwable.message}, for url ${request.url}")
            true
        }
        retryIf(maxRetries) { request, response ->
            if (response.status.value.let { it in 500..599 }) {
                log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                true
            } else {
                false
            }
        }
    }
}

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache, config).get(wellKnownUrl).body<WellKnown>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)

fun getWellKnownTokenX(wellKnownUrl: String) =
    runBlocking { HttpClient(Apache, config).get(wellKnownUrl).body<WellKnownTokenX>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
