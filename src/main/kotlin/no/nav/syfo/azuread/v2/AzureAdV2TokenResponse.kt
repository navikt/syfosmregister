package no.nav.syfo.azuread.v2

import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AzureAdV2TokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    val tokenType: String,
)

fun AzureAdV2TokenResponse.toAzureAdV2Token(): AzureAdV2Token {
    val expiresOn = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(this.expiresIn)
    return AzureAdV2Token(
        accessToken = this.accessToken,
        expires = expiresOn,
    )
}
