package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.azuread.v2.AzureAdV2Client
import org.apache.http.HttpHeaders
import org.slf4j.LoggerFactory

class TilgangskontrollService(
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val url: String,
    private val syfotilgangskontrollClientId: String
) {
    companion object {
        val log = LoggerFactory.getLogger(TilgangskontrollService::class.java)
        const val TILGANGSKONTROLL_V2_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/bruker"
        const val TILGANGSKONTROLL_V1_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/bruker?fnr="
    }

    suspend fun hasAccessToUser(fnr: String, accessToken: String): Boolean {
        return hasAccess(accessToken, getTilgangskontrollUrl(fnr))
    }

    suspend fun hasAccessToUserOboToken(fnr: String, accessToken: String): Boolean {
        log.info("checking access to user with obo-token")
        val oboToken = azureAdV2Client.getOnBehalfOfToken(scopeClientId = syfotilgangskontrollClientId, token = accessToken)
            ?.accessToken

        if (oboToken != null) {
            log.info("Got obo-token checking access for ${getTilgangskontrollV2Url("FNR")}")
            return hasAccess(oboToken, getTilgangskontrollV2Url(fnr))
        } else {
            log.info("did not get obo-token")
            return false
        }
    }

    private suspend fun hasAccess(accessToken: String, requestUrl: String): Boolean {
        val response: HttpResponse = httpClient.get(requestUrl) {
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

    private fun getTilgangskontrollV2Url(fnr: String): String {
        return "$url$TILGANGSKONTROLL_V2_PERSON_PATH/$fnr"
    }

    private fun getTilgangskontrollUrl(fnr: String): String {
        return "$url$TILGANGSKONTROLL_V1_PERSON_PATH$fnr"
    }
}
