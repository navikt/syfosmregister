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
import java.sql.ResultSet
import java.time.LocalDateTime
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingSendApi
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SendSykmeldingEndeTilEndeSpek : Spek({

    val database = TestDB()
    val sykmeldingStatusService = SykmeldingStatusService(database)

    beforeEachTest {
        database.connection.opprettSykmeldingsopplysninger(testSykmeldingsopplysninger)
        database.connection.opprettSykmeldingsdokument(testSykmeldingsdokument)
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Ende-til-ende-test for sending av sykmeldinger") {
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
            application.routing { registerSykmeldingSendApi(sykmeldingStatusService) }

            it("Send lagrer riktig info i databasen") {
                val sykmeldingId = "uuid"
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTOForArbeidstaker()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val arbeidsgiver = database.finnArbeidsgiverForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)
                    val svar = sporsmal[0].svar

                    sporsmal.size shouldEqual 1
                    sporsmal[0].tekst shouldEqual "Jeg er sykmeldt fra "
                    sporsmal[0].shortName shouldEqual ShortName.ARBEIDSSITUASJON
                    svar.svartype shouldEqual Svartype.ARBEIDSSITUASJON
                    svar.svar shouldEqual "ARBEIDSTAKER"

                    arbeidsgiver.juridiskOrgnummer shouldEqual null
                    arbeidsgiver.orgnummer shouldEqual "123456"
                    arbeidsgiver.orgnavn shouldEqual "Bedrift A/S"
                }
            }
        }
    }
})

fun DatabaseInterface.finnSvarForSykmelding(sykmeldingId: String): List<Sporsmal> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM svar as SVAR
                     INNER JOIN sporsmal as SPM on SVAR.sporsmal_id = SPM.id
                WHERE sykmelding_id=?;
                """
        ).use {
            it.setString(1, sykmeldingId)
            return it.executeQuery().toList { tilSporsmal() }
        }
    }
}

fun ResultSet.tilSporsmal(): Sporsmal =
    Sporsmal(
        tekst = getString("tekst"),
        shortName = ShortName.valueOf(getString("shortname")),
        svar = Svar(
            sykmeldingId = getString("sykmelding_id"),
            sporsmalId = getInt("sporsmal_id"),
            svartype = Svartype.valueOf(getString("svartype")),
            svar = getString("svar")
        )
    )

fun DatabaseInterface.finnArbeidsgiverForSykmelding(sykmeldingId: String): Arbeidsgiver {
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM arbeidsgiver
                WHERE sykmelding_id=?;
                """
        ).use {
            it.setString(1, sykmeldingId)
            return it.executeQuery().toList { tilArbeidsgiver() }.first()
        }
    }
}

fun ResultSet.tilArbeidsgiver(): Arbeidsgiver =
    Arbeidsgiver(
        sykmeldingId = getString("sykmelding_id"),
        orgnummer = getString("orgnummer"),
        orgnavn = getString("navn"),
        juridiskOrgnummer = getString("juridisk_orgnummer")
    )

fun opprettSykmeldingSendEventDTOForArbeidstaker(): SykmeldingSendEventDTO =
    SykmeldingSendEventDTO(
        LocalDateTime.now(),
        ArbeidsgiverDTO(orgnummer = "123456", juridiskOrgnummer = null, orgNavn = "Bedrift A/S"),
        listOf(SporsmalOgSvarDTO("Jeg er sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
            SporsmalOgSvarDTO("Er Ole Olsen din nærmeste leder?", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI"))
    )
