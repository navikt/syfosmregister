package no.nav.syfo.sykmelding.internal.tilgang

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.apache.http.HttpHeaders
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TilgangskontrollService(
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    url: String,
    private val syfotilgangskontrollScope: String
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TilgangskontrollService::class.java)
        const val TILGANGSKONTROLL_PERSON_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/person"
    }

    private val tilgangskontrollPersonUrl: String

    init {
        tilgangskontrollPersonUrl = "$url$TILGANGSKONTROLL_PERSON_PATH"
    }

    suspend fun hasAccessToUserOboToken(fnr: String, accessToken: String): Boolean {
        val oboToken = azureAdV2Client.getOnBehalfOfToken(scope = syfotilgangskontrollScope, token = accessToken)
            ?.accessToken

        return if (oboToken != null) {
            hasAccess(oboToken, tilgangskontrollPersonUrl, fnr)
        } else {
            log.info("did not get obo-token")
            false
        }
    }

    private suspend fun hasAccess(accessToken: String, requestUrl: String, fnr: String): Boolean {
        return try {
            httpClient.get(requestUrl) {
                accept(ContentType.Application.Json)
                headers.append(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                headers.append(NAV_PERSONIDENT_HEADER, fnr)
            }.body<Tilgang>().harTilgang
        } catch (e: ResponseException) {
            log.info("Ingen tilgang, syfotilgangskontroll returnerte statuskode ${e.response.status.value}")
            false
        } catch (e: Exception) {
            log.error("Noe gikk galt ved sjekk mot syfotilgangskontroll", e)
            throw e
        }
    }
}
