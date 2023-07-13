package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.log

fun HttpClientConfig<ApacheEngineConfig>.handleResponseException() {
    HttpResponseValidator {
        handleResponseExceptionWithRequest { exception, _ ->
            when (exception) {
                is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
            }
        }
    }
}

fun HttpClientConfig<ApacheEngineConfig>.setupJacksonSerialization() {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

fun HttpClientConfig<ApacheEngineConfig>.setupRetry() {
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
