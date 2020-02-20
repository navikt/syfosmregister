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
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.registerSykmeldingSendApi
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusBackupKafkaProducer
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
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusBackupKafkaProducer::class)
    val sykmeldingStatusService = SykmeldingStatusService(database, sykmeldingStatusKafkaProducer)

    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt, StatusEvent.APEN, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC)))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
        every { sykmeldingStatusKafkaProducer.send(any()) } just Runs
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
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val arbeidsgiver = database.finnArbeidsgiverStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)
                    val svar = sporsmal[0].svar

                    status shouldEqual StatusEvent.SENDT
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

            it("Send overskriver eller sletter tidligere svar i databasen") {
                val sykmeldingId = "uuid"
                database.registrerBekreftet(SykmeldingBekreftEvent(sykmeldingId, LocalDateTime.now().minusMinutes(5), listOf(
                    Sporsmal("Sykmeldt fra ", ShortName.ARBEIDSSITUASJON, Svar(sykmeldingId, 1, Svartype.ARBEIDSSITUASJON, "Selvstendig")),
                    Sporsmal("Har forsikring?", ShortName.FORSIKRING, Svar(sykmeldingId, 2, Svartype.JA_NEI, "Nei"))
                )), SykmeldingStatusEvent(sykmeldingId, LocalDateTime.now().minusMinutes(5), StatusEvent.BEKREFTET))

                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTOForArbeidstaker()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                }) {
                    val status = database.finnStatusForSykmelding(sykmeldingId)
                    val arbeidsgiver = database.finnArbeidsgiverStatusForSykmelding(sykmeldingId)
                    val sporsmal = database.finnSvarForSykmelding(sykmeldingId)
                    val svar = sporsmal[0].svar

                    status shouldEqual StatusEvent.SENDT
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

private fun opprettSykmeldingSendEventDTOForArbeidstaker(): SykmeldingSendEventDTO =
    SykmeldingSendEventDTO(
        LocalDateTime.now(),
        ArbeidsgiverStatusDTO(orgnummer = "123456", juridiskOrgnummer = null, orgNavn = "Bedrift A/S"),
        listOf(SporsmalOgSvarDTO("Jeg er sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
            SporsmalOgSvarDTO("Er Ole Olsen din nærmeste leder?", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI"))
    )
