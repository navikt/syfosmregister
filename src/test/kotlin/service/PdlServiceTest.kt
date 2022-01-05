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
import no.nav.syfo.pdl.error.PersonNotFoundException
import no.nav.syfo.pdl.service.PdlPersonService
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import kotlin.test.assertFailsWith

internal class PdlServiceTest : Spek({

    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AzureAdV2Client::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, "scope")

    afterEachTest {
    }

    describe("Test PdlPersonService") {

        it("Skal kunne hent person fra pdl") {
            coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
                "token",
                OffsetDateTime.now().plusHours(1)
            )
            coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse(
                data = PdlResponse(
                    hentIdenter = Identliste(
                        listOf(
                            IdentInformasjon(ident = "01245678901", gruppe = "FOLKEREGISTERIDENT", historisk = false)
                        )
                    )
                ),
                errors = null
            )

            runBlocking {
                val person = pdlService.getPdlPerson("01245678901")
                person.fnr shouldBeEqualTo "01245678901"
            }
        }

        it("Skal feile når person ikke finnes") {
            coEvery { accessTokenClientV2.getAccessToken(any()) } returns AzureAdV2Token(
                "token",
                OffsetDateTime.now().plusHours(1)
            )
            coEvery { pdlClient.getPerson(any(), any()) } returns GraphQLResponse(
                PdlResponse(
                    hentIdenter = Identliste(emptyList())
                ),
                errors = null
            )
            val exception = assertFailsWith<PersonNotFoundException> {
                runBlocking {
                    pdlService.getPdlPerson("123")
                }
            }
            exception.message shouldBeEqualTo "Fant ikke person i PDL"
        }
    }
})
