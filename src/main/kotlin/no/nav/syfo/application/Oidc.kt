package no.nav.syfo.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.exception.ServiceUnavailableException

val config: HttpClientConfig<CIOEngineConfig>.() -> Unit = {
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
        maxRetries = 3
        delayMillis { retry ->
            retry * 500L
        }
    }
}

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(CIO, config).get(wellKnownUrl).body<WellKnown>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)

fun getWellKnownTokenX(wellKnownUrl: String) =
    runBlocking { HttpClient(CIO, config).get(wellKnownUrl).body<WellKnownTokenX>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
