package no.nav.syfo.identendring

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
import no.nav.syfo.pdl.client.model.IdentInformasjon
import no.nav.syfo.pdl.error.InactiveIdentException
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmelding.db.getSykmeldinger
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.status.ArbeidsgiverStatus
import no.nav.syfo.sykmelding.status.ShortName
import no.nav.syfo.sykmelding.status.Sporsmal
import no.nav.syfo.sykmelding.status.StatusEvent
import no.nav.syfo.sykmelding.status.Svar
import no.nav.syfo.sykmelding.status.Svartype
import no.nav.syfo.sykmelding.status.SykmeldingSendEvent
import no.nav.syfo.sykmelding.status.SykmeldingStatusEvent
import no.nav.syfo.sykmelding.status.registerStatus
import no.nav.syfo.sykmelding.status.registrerSendt
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith

class IdentendringServiceTest : FunSpec({
    val sendtSykmeldingKafkaProducer = mockk<SendtSykmeldingKafkaProducer>(relaxed = true)
    val database = TestDB.database
    val pdlService = mockk<PdlPersonService>(relaxed = true)
    val identendringService = IdentendringService(database, sendtSykmeldingKafkaProducer, pdlService)

    afterTest {
        database.connection.dropData()
        clearMocks(sendtSykmeldingKafkaProducer)
    }
    afterSpec {
        TestDB.stop()
    }

    context("IdentendringService") {
        test("Endrer ingenting hvis det ikke er endring i fnr") {
            val identListeUtenEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.AKTORID)
            )

            identendringService.oppdaterIdent(identListeUtenEndringIFnr) shouldBeEqualTo 0
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        test("Endrer ingenting hvis det ikke finnes sykmeldinger på gammelt fnr") {
            val identListeMedEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )

            coEvery { pdlService.getPdlPerson(any()) } returns PdlPerson(listOf(IdentInformasjon("1234", false, "FOLKEREGISTERIDENT")))

            identendringService.oppdaterIdent(identListeMedEndringIFnr) shouldBeEqualTo 0
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }

        test("Kaster feil hvis nytt fnr ikke stemmer med fnr fra PDL") {
            val gammeltFnr = "2222"
            val identListeMedEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = gammeltFnr, gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )

            val idNySykmelding = UUID.randomUUID().toString()
            val idSendtSykmelding = UUID.randomUUID().toString()
            val idGammelSendtSykmelding = UUID.randomUUID().toString()
            forberedTestsykmeldinger(database = database, gammeltFnr = gammeltFnr, idNySykmelding = idNySykmelding, idSendtSykmelding = idSendtSykmelding, idGammelSendtSykmelding = idGammelSendtSykmelding)

            coEvery { pdlService.getPdlPerson(any()) } returns PdlPerson(listOf(IdentInformasjon(gammeltFnr, false, "FOLKEREGISTERIDENT")))

            assertFailsWith<InactiveIdentException> {
                identendringService.oppdaterIdent(identListeMedEndringIFnr)
            }
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }

        test("Oppdaterer i databasen og resender sendte sykmeldinger fra de siste 4 mnd ved nytt fnr") {
            val gammeltFnr = "12345678910"
            val nyttFnr = "10987654321"
            val idNySykmelding = UUID.randomUUID().toString()
            val idSendtSykmelding = UUID.randomUUID().toString()
            val idGammelSendtSykmelding = UUID.randomUUID().toString()
            forberedTestsykmeldinger(database = database, gammeltFnr = gammeltFnr, idNySykmelding = idNySykmelding, idSendtSykmelding = idSendtSykmelding, idGammelSendtSykmelding = idGammelSendtSykmelding)
            val identListe = listOf(
                Ident(idnummer = nyttFnr, gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = gammeltFnr, gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )

            coEvery { pdlService.getPdlPerson(any()) } returns PdlPerson(listOf(IdentInformasjon("10987654321", false, "FOLKEREGISTERIDENT")))
            identendringService.oppdaterIdent(identListe) shouldBeEqualTo 3
            coVerify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(match { it.kafkaMetadata.sykmeldingId == idSendtSykmelding && it.kafkaMetadata.fnr == nyttFnr }) }
            coVerify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(match { it.kafkaMetadata.sykmeldingId != idSendtSykmelding }) }
            database.getSykmeldinger(gammeltFnr).size shouldBeEqualTo 0
            database.getSykmeldinger(nyttFnr).size shouldBeEqualTo 3
        }
    }
})

suspend fun forberedTestsykmeldinger(database: DatabaseInterface, gammeltFnr: String, idNySykmelding: String, idSendtSykmelding: String, idGammelSendtSykmelding: String) {
    database.lagreMottattSykmelding(testSykmeldingsopplysninger.copy(id = idNySykmelding, pasientFnr = gammeltFnr), testSykmeldingsdokument.copy(id = idNySykmelding))
    database.registerStatus(
        SykmeldingStatusEvent(
            idNySykmelding,
            testSykmeldingsopplysninger.mottattTidspunkt.atOffset(
                ZoneOffset.UTC
            ),
            StatusEvent.APEN
        )
    )
    database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = idNySykmelding))

    database.lagreMottattSykmelding(testSykmeldingsopplysninger.copy(id = idSendtSykmelding, pasientFnr = gammeltFnr), testSykmeldingsdokument.copy(id = idSendtSykmelding))
    database.registerStatus(
        SykmeldingStatusEvent(
            idSendtSykmelding, getNowTickMillisOffsetDateTime().minusDays(10), StatusEvent.APEN
        )
    )
    database.registrerSendt(
        SykmeldingSendEvent(
            idSendtSykmelding, getNowTickMillisOffsetDateTime(),
            ArbeidsgiverStatus(idSendtSykmelding, "orgnummer", null, "Bedrift"),
            listOf(Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON, Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER")))
        ),
        SykmeldingStatusEvent(idSendtSykmelding, getNowTickMillisOffsetDateTime(), StatusEvent.SENDT)
    )
    database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = idSendtSykmelding))

    database.lagreMottattSykmelding(
        testSykmeldingsopplysninger.copy(id = idGammelSendtSykmelding, pasientFnr = gammeltFnr),
        testSykmeldingsdokument.copy(
            id = idGammelSendtSykmelding,
            sykmelding = testSykmeldingsdokument.sykmelding.copy(
                perioder = listOf(
                    Periode(
                        fom = LocalDate.now().minusMonths(8),
                        tom = LocalDate.now().minusMonths(6),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(
                            medisinskArsak = MedisinskArsak(null, emptyList()),
                            arbeidsrelatertArsak = ArbeidsrelatertArsak(null, emptyList())
                        ),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = Gradert(false, 0),
                        reisetilskudd = false
                    )
                )
            )
        )
    )
    database.registerStatus(
        SykmeldingStatusEvent(
            idGammelSendtSykmelding, getNowTickMillisOffsetDateTime().minusMonths(8), StatusEvent.APEN
        )
    )
    database.registrerSendt(
        SykmeldingSendEvent(
            idGammelSendtSykmelding, getNowTickMillisOffsetDateTime().minusMonths(6),
            ArbeidsgiverStatus(idGammelSendtSykmelding, "orgnummer", null, "Bedrift"),
            listOf(Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON, Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER")))
        ),
        SykmeldingStatusEvent(idGammelSendtSykmelding, getNowTickMillisOffsetDateTime().minusMonths(6), StatusEvent.SENDT)
    )
    database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = idGammelSendtSykmelding))
}
