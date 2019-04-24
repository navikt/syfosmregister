package no.nav.syfo.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking

fun getWellKnown(wellKnownUrl: String) = runBlocking { HttpClient(Apache).get<WellKnown>(wellKnownUrl) }

data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
