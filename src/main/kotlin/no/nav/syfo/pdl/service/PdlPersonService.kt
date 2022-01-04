package no.nav.syfo.pdl.service

import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.model.PdlPerson
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val azureAdV2Client: AzureAdV2Client,
    private val pdlScope: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(PdlPersonService::class.java)
    }

    suspend fun getPdlPerson(fnr: String): PdlPerson {
        val token = azureAdV2Client.getAccessToken(pdlScope)?.accessToken
            ?: throw RuntimeException("Klarte ikke hente accessToken for PDL")
        val pdlResponse = pdlClient.getPerson(fnr, token)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL kastet error: {} ", it)
            }
        }
        if (pdlResponse.data.hentIdenter == null || pdlResponse.data.hentIdenter.identer.isNullOrEmpty()) {
            log.warn("Fant ikke person i PDL {}")
            throw AktoerNotFoundException("Fant ikke person i PDL")
        }
        return PdlPerson(pdlResponse.data.hentIdenter.identer)
    }
}
