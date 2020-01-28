package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import org.apache.http.HttpHeaders
import org.slf4j.LoggerFactory

class TilgangskontrollService(private val httpClient: HttpClient, private val url: String) {
    companion object {
        val log = LoggerFactory.getLogger(TilgangskontrollService::class.java)
    }
    suspend fun hasAccessToUser(fnr: String, accessToken: String): Boolean {
        val response: HttpResponse = httpClient.get("$url/$fnr") {
            accept(ContentType.Application.Json)
            headers.append(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.receive<Tilgang>().harTilgang
            else -> {
                log.info("Ingen tilgang, Tilgangskontroll returnerte Status : {}", response.status)
                false
            }
        }
    }
}
