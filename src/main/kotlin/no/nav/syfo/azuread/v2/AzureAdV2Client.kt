package no.nav.syfo.azuread.v2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import org.slf4j.LoggerFactory

class AzureAdV2Client(
    private val azureAppClientId: String,
    private val azureAppClientSecret: String,
    private val azureTokenEndpoint: String,
    private val httpClient: HttpClient,
    private val azureAdV2Cache: AzureAdV2Cache = AzureAdV2Cache(),
) {

    /**
     * Returns a non-obo access token authenticated using app specific client credentials
     */
    suspend fun getAccessToken(
        scope: String,
    ): AzureAdV2Token? {
        return azureAdV2Cache.getToken(scope)
            ?: getClientSecretAccessToken(scope)?.let {
                azureAdV2Cache.putValue(scope, it)
            }
    }

    private suspend fun getClientSecretAccessToken(
        scope: String,
    ): AzureAdV2Token? {
        return getOboAccessToken(
            Parameters.build {
                append("client_id", azureAppClientId)
                append("client_secret", azureAppClientSecret)
                append("scope", scope)
                append("grant_type", "client_credentials")
            },
        )?.toAzureAdV2Token()
    }

    /**
     * Returns a obo-token for a given user
     */
    suspend fun getOnBehalfOfToken(
        scope: String,
        token: String,
    ): AzureAdV2Token? {
        return azureAdV2Cache.getToken(token)
            ?: getOboAccessToken(token, scope)?.let {
                azureAdV2Cache.putValue(token, it)
            }
    }

    private suspend fun getOboAccessToken(
        token: String,
        scopeClientId: String,
    ): AzureAdV2Token? {
        return getOboAccessToken(
            Parameters.build {
                append("client_id", azureAppClientId)
                append("client_secret", azureAppClientSecret)
                append("client_assertion_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", token)
                append("scope", scopeClientId)
                append("requested_token_use", "on_behalf_of")
            },
        )?.toAzureAdV2Token()
    }

    private suspend fun getOboAccessToken(
        formParameters: Parameters,
    ): AzureAdV2TokenResponse? {
        return try {
            val response: HttpResponse = httpClient.post(azureTokenEndpoint) {
                accept(ContentType.Application.Json)
                setBody(FormDataContent(formParameters))
            }
            response.body<AzureAdV2TokenResponse>()
        } catch (e: ClientRequestException) {
            log.warn("Client error while requesting AzureAdAccessToken with statusCode=${e.response.status.value}", e)
            return null
        } catch (e: ServerResponseException) {
            log.error("Server error while requesting AzureAdAccessToken with statusCode=${e.response.status.value}", e)
            return null
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av obo-accesstoken", e)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AzureAdV2Client::class.java)
    }
}
