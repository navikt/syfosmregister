package service

import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2Token
import no.nav.syfo.graphql.model.GraphQLResponse
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.client.model.Identliste
import no.nav.syfo.pdl.client.model.PdlResponse
import no.nav.syfo.pdl.error.AktoerNotFoundException
import no.nav.syfo.pdl.error.PersonNotFoundInPdl
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutil.getPdlResponse
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.time.OffsetDateTime
import kotlin.test.assertFailsWith

internal class PdlServiceTest {

    private val pdlClient = mockkClass(PdlClient::class)
    private val accessTokenClientV2 = mockkClass(AzureAdV2Client::class)
    private val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "scope")

    @Test
    internal fun `Hent person fra pdl`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )
        coEvery { pdlClient.getPerson(any(), any()) } returns getPdlResponse()

        runBlocking {
            val person = pdlService.getPdlPerson("01245678901")
            person.fnr shouldBeEqualTo "01245678901"
        }
    }

    @Test
    internal fun `Skal feile når person ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(null),
            errors = null
        )

        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123")
            }
        }
        exception.message shouldBeEqualTo "Klarte ikke hente ut person fra PDL"
    }

    @Test
    internal fun `Skal feile når navn er tom liste`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentIdenter = Identliste(emptyList())
            ),
            errors = null
        )
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123")
            }
        }
        exception.message shouldBeEqualTo "Fant ikke navn på person i PDL"
    }

    @Test
    internal fun `Skal feile når navn ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentIdenter = Identliste(
                    listOf(
                        IdentInformasjon(
                            ident = "987654321",
                            gruppe = "foo",
                            historisk = false
                        )
                    )
                )
            ),
            errors = null
        )
        val exception = assertFailsWith<PersonNotFoundInPdl> {
            runBlocking {
                pdlService.getPdlPerson("123")
            }
        }
        exception.message shouldBeEqualTo "Fant ikke navn på person i PDL"
    }

    @Test
    internal fun `Skal feile når aktørid ikke finnes`() {
        coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
            "token",
            OffsetDateTime.now().plusHours(1)
        )
        coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse<PdlResponse>(
            PdlResponse(
                hentIdenter = Identliste(emptyList())
            ),
            errors = null
        )
        val exception = assertFailsWith<AktoerNotFoundException> {
            runBlocking {
                pdlService.getPdlPerson("123")
            }
        }
        exception.message shouldBeEqualTo "Fant ikke aktørId i PDL"
    }
}
