package no.nav.syfo.identendring

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.identendring.model.Ident
import no.nav.syfo.identendring.model.IdentType
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
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
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class IdentendringServiceTest : Spek({
    val sendtSykmeldingKafkaProducer = mockk<SendtSykmeldingKafkaProducer>(relaxed = true)
    val database = TestDB()
    val identendringService = IdentendringService(database, sendtSykmeldingKafkaProducer)

    afterEachTest {
        database.connection.dropData()
        clearMocks(sendtSykmeldingKafkaProducer)
    }
    afterGroup {
        database.stop()
    }

    describe("IdentendringService") {
        it("Endrer ingenting hvis det ikke er endring i fnr") {
            val identListeUtenEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.AKTORID)
            )

            identendringService.oppdaterIdent(identListeUtenEndringIFnr) shouldBeEqualTo 0
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        it("Endrer ingenting hvis det ikke finnes sykmeldinger på gammelt fnr") {
            val identListeMedEndringIFnr = listOf(
                Ident(idnummer = "1234", gjeldende = true, type = IdentType.FOLKEREGISTERIDENT),
                Ident(idnummer = "1111", gjeldende = true, type = IdentType.AKTORID),
                Ident(idnummer = "2222", gjeldende = false, type = IdentType.FOLKEREGISTERIDENT)
            )

            identendringService.oppdaterIdent(identListeMedEndringIFnr) shouldBeEqualTo 0
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(any()) }
        }
        it("Oppdaterer i databasen og resender sendte sykmeldinger fra de siste 4 mnd ved nytt fnr") {
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

            identendringService.oppdaterIdent(identListe) shouldBeEqualTo 3
            verify(exactly = 1) { sendtSykmeldingKafkaProducer.sendSykmelding(match { it.kafkaMetadata.sykmeldingId == idSendtSykmelding && it.kafkaMetadata.fnr == nyttFnr }) }
            verify(exactly = 0) { sendtSykmeldingKafkaProducer.sendSykmelding(match { it.kafkaMetadata.sykmeldingId != idSendtSykmelding }) }
            database.getSykmeldinger(gammeltFnr).size shouldBeEqualTo 0
            database.getSykmeldinger(nyttFnr).size shouldBeEqualTo 3
        }
    }
})

fun forberedTestsykmeldinger(database: TestDB, gammeltFnr: String, idNySykmelding: String, idSendtSykmelding: String, idGammelSendtSykmelding: String) {
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
            idSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC).minusDays(10), StatusEvent.APEN
        )
    )
    database.registrerSendt(
        SykmeldingSendEvent(
            idSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC),
            ArbeidsgiverStatus(idSendtSykmelding, "orgnummer", null, "Bedrift"),
            Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON, Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))
        ),
        SykmeldingStatusEvent(idSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC), StatusEvent.SENDT)
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
            idGammelSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC).minusMonths(8), StatusEvent.APEN
        )
    )
    database.registrerSendt(
        SykmeldingSendEvent(
            idGammelSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC).minusMonths(6),
            ArbeidsgiverStatus(idGammelSendtSykmelding, "orgnummer", null, "Bedrift"),
            Sporsmal("Arbeidssituasjon", ShortName.ARBEIDSSITUASJON, Svar("uuid", 1, Svartype.ARBEIDSSITUASJON, "ARBEIDSTAKER"))
        ),
        SykmeldingStatusEvent(idGammelSendtSykmelding, OffsetDateTime.now(ZoneOffset.UTC).minusMonths(6), StatusEvent.SENDT)
    )
    database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = idGammelSendtSykmelding))
}
