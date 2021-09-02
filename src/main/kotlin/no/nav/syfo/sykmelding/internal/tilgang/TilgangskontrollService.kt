package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.apache.http.HttpHeaders
import org.slf4j.LoggerFactory

class TilgangskontrollService(
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    url: String,
    private val syfotilgangskontrollClientId: String
) {
    companion object {
        val log = LoggerFactory.getLogger(TilgangskontrollService::class.java)
        const val TILGANGSKONTROLL_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/person"
    }

    private val tilgangskontrollPersonUrl: String

    init {
        tilgangskontrollPersonUrl = "$url$TILGANGSKONTROLL_PERSON_PATH"
    }

    suspend fun hasAccessToUserOboToken(fnr: String, accessToken: String): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(scopeClientId = syfotilgangskontrollClientId, token = accessToken)
            ?.accessToken

        if (oboToken != null) {
            return hasAccess(oboToken, tilgangskontrollPersonUrl, fnr)
        } else {
            log.info("did not get obo-token")
            return false
        }
    }

    private suspend fun hasAccess(accessToken: String, requestUrl: String, fnr: String): Boolean {
        val response: HttpResponse = httpClient.get(requestUrl) {
            accept(ContentType.Application.Json)
            headers.append(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            headers.append(NAV_PERSONIDENT_HEADER, fnr)
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
