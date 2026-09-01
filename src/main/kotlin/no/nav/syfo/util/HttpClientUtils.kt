package no.nav.syfo.util

import io.ktor.client.*
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson3.jackson
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.log

fun HttpClientConfig<Apache5EngineConfig>.handleResponseException() {
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            when (exception) {
                is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
            }
        }
    }
}

fun HttpClientConfig<Apache5EngineConfig>.setupJacksonSerialization() {
    install(ContentNegotiation) { jackson {} }
}

fun HttpClientConfig<Apache5EngineConfig>.setupRetry() {
    install(HttpRequestRetry) {
        constantDelay(100, 0, false)
        retryOnExceptionIf(3) { request, throwable ->
            log.warn("Caught exception ${throwable.message}, for url ${request.url}")
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
