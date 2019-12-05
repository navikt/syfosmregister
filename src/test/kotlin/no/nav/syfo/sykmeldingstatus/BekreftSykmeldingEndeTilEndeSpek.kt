package no.nav.syfo.sykmeldingstatus

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import java.time.LocalDateTime
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingBekreftApi
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BekreftSykmeldingEndeTilEndeSpek : Spek({

    val database = TestDB()
    val sykmeldingStatusService = SykmeldingStatusService(database)

    beforeEachTest {
        database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
        database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, LocalDateTime.now(), StatusEvent.APEN))
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Ende-til-ende-test for bekrefting av sykmeldinger") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            application.routing { registerSykmeldingBekreftApi(sykmeldingStatusService) }

            it("Bekreft med spørsmål lagrer riktig info i databasen") {
                val sykmeldingId = "uuid"
                val timestamp = LocalDateTime.now()
                val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(sykmeldingBekreftEventDTO))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)

                    status shouldEqual StatusEvent.BEKREFTET
                    sporsmal.size shouldEqual 4
                    sporsmal[0] shouldEqual Sporsmal("Sykmeldt fra ", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, 1, Svartype.ARBEIDSSITUASJON, "Selvstendig"))
                    sporsmal[1] shouldEqual Sporsmal("Har forsikring?", ShortName.FORSIKRING, Svar(sykmeldingId, 2, Svartype.JA_NEI, "Ja"))
                    sporsmal[2] shouldEqual Sporsmal("Hatt fravær?", ShortName.FRAVAER, Svar(sykmeldingId, 3, Svartype.JA_NEI, "Ja"))
                    sporsmal[3] shouldEqual Sporsmal("Når hadde du fravær?", ShortName.PERIODE, Svar(sykmeldingId, 4, Svartype.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
                }
            }

            it("Bekreft uten spørsmål lagrer riktig info i databasen") {
                val sykmeldingId = "annenUuid"
                val timestamp = LocalDateTime.now()
                val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, null)
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(sykmeldingBekreftEventDTO))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)

                    status shouldEqual StatusEvent.BEKREFTET
                    sporsmal.size shouldEqual 0
                }
            }

            it("Bekreft med spørsmål overskriver tidligere svar i databasen") {
                val sykmeldingId = "uuid"
                val spmOgSvarListe = listOf(SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Selvstendig"),
                    SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Nei"))

                handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(SykmeldingBekreftEventDTO(LocalDateTime.now(), spmOgSvarListe)))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }

                val timestamp = LocalDateTime.now()
                val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, lagSporsmalOgSvarDTOListe())
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(sykmeldingBekreftEventDTO))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)

                    status shouldEqual StatusEvent.BEKREFTET
                    sporsmal.size shouldEqual 4
                    sporsmal[0] shouldEqualSporsmal Sporsmal("Sykmeldt fra ", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, 1, Svartype.ARBEIDSSITUASJON, "Selvstendig"))
                    sporsmal[1] shouldEqualSporsmal Sporsmal("Har forsikring?", ShortName.FORSIKRING, Svar(sykmeldingId, 2, Svartype.JA_NEI, "Ja"))
                    sporsmal[2] shouldEqualSporsmal Sporsmal("Hatt fravær?", ShortName.FRAVAER, Svar(sykmeldingId, 3, Svartype.JA_NEI, "Ja"))
                    sporsmal[3] shouldEqualSporsmal Sporsmal("Når hadde du fravær?", ShortName.PERIODE, Svar(sykmeldingId, 4, Svartype.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
                }
            }

            it("Bekreft uten spørsmål overskriver tidligere svar i databasen") {
                val sykmeldingId = "uuid"
                handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(SykmeldingBekreftEventDTO(LocalDateTime.now(), lagSporsmalOgSvarDTOListe())))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }

                val timestamp = LocalDateTime.now()
                val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(timestamp, null)
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(sykmeldingBekreftEventDTO))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)

                    status shouldEqual StatusEvent.BEKREFTET
                    sporsmal.size shouldEqual 0
                }
            }
        }
    }
})

private fun lagSporsmalOgSvarDTOListe(): List<SporsmalOgSvarDTO> {
    return listOf(SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Selvstendig"),
        SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
        SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
}

infix fun Sporsmal.shouldEqualSporsmal(expected: Sporsmal): Sporsmal = this.apply { sporsmalErLike(expected, this) }

fun sporsmalErLike(forventetSporsmal: Sporsmal, faktiskSporsmal: Sporsmal): Boolean {
    if (forventetSporsmal.shortName == faktiskSporsmal.shortName && forventetSporsmal.tekst == faktiskSporsmal.tekst && svarErLike(forventetSporsmal.svar, faktiskSporsmal.svar)) {
        return true
    }
    return false
}

fun svarErLike(forventetSvar: Svar, faktiskSvar: Svar): Boolean {
    if (forventetSvar.sykmeldingId == faktiskSvar.sykmeldingId && forventetSvar.svar == faktiskSvar.svar && forventetSvar.svartype == faktiskSvar.svartype) {
        return true
    }
    return false
}
