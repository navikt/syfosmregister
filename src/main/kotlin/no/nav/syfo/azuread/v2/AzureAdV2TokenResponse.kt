package no.nav.syfo.azuread.v2

import java.time.LocalDateTime

data class AzureAdV2TokenResponse(
    val access_token: String,
    val expires_in: Long,
    val token_type: String
)

fun AzureAdV2TokenResponse.toAzureAdV2Token(): AzureAdV2Token {
    val expiresOn = LocalDateTime.now().plusSeconds(this.expires_in)
    return AzureAdV2Token(
        accessToken = this.access_token,
        expires = expiresOn
    )
}
