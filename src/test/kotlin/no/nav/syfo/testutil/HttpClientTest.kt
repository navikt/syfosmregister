package no.nav.syfo.testutil

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson

data class ResponseData(val httpStatusCode: HttpStatusCode, val content: String, val headers: Headers = headersOf("Content-Type", listOf("application/json")))

class HttpClientTest {

    var responseData: ResponseData? = null

    val httpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            jackson {}
        }
        expectSuccess = false
        engine {
            addHandler { request ->
                respond(responseData!!.content, responseData!!.httpStatusCode, responseData!!.headers)
            }
        }
    }
}
