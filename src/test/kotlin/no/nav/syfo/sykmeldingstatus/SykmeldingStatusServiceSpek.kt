package no.nav.syfo.sykmeldingstatus

import io.mockk.every
import io.mockk.mockkClass
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.SykmeldingService
import no.nav.syfo.persistering.lagreMottattSykmelding
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusBackupKafkaProducer
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.testBehandlingsutfall
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusServiceSpek : Spek({

    val database = TestDB()
    val sykmeldingService = SykmeldingService(database)
    val sykmeldingStatusKafkaProducer = mockkClass(SykmeldingStatusBackupKafkaProducer::class)
    val sykmeldingStatusService = SykmeldingStatusService(database, sykmeldingStatusKafkaProducer)
    every { sykmeldingStatusKafkaProducer.send(any()) } returns Unit

    beforeEachTest {
        database.lagreMottattSykmelding(testSykmeldingsopplysninger, testSykmeldingsdokument)
        database.registerStatus(SykmeldingStatusEvent(testSykmeldingsopplysninger.id, testSykmeldingsopplysninger.mottattTidspunkt, StatusEvent.APEN, testSykmeldingsopplysninger.mottattTidspunkt.atOffset(ZoneOffset.UTC)))
        database.connection.opprettBehandlingsutfall(testBehandlingsutfall)
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Test registrerStatus") {
        it("BekreftetDato skal være null når sykmelding ikke er bekreftet") {
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldBe null
            savedSykmelding.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.APEN
        }

        it("Skal få bekreftetDato hvis sykmelding er bekreftet") {
            val confirmedDateTime = LocalDateTime.now()
            sykmeldingStatusService.registrerStatus(SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET))
            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
            savedSykmelding.sykmeldingStatus.statusEvent shouldEqual StatusEventDTO.BEKREFTET
        }

        it("Skal ikke kaste feil hvis man oppdaterer med eksisterende status på nytt") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.BEKREFTET)
            sykmeldingStatusService.registrerStatus(status)
            sykmeldingStatusService.registrerStatus(status)

            val savedSykmelding = sykmeldingService.hentSykmeldinger("pasientFnr")[0]
            savedSykmelding.bekreftetDato shouldEqual confirmedDateTime
        }

        it("Skal ikke hente sykmeldinger med status SLETTET") {
            val confirmedDateTime = LocalDateTime.now()
            val status = SykmeldingStatusEvent("uuid", confirmedDateTime, StatusEvent.APEN)
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            sykmeldingStatusService.registrerStatus(status)
            database.registerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger shouldEqual emptyList()
        }

        it("Skal kun hente sykmeldinger der status ikke er SLETTET") {
            val copySykmeldingDokument = testSykmeldingsdokument.copy(id = "uuid2")
            val copySykmeldingopplysning = testSykmeldingsopplysninger.copy(
                    id = "uuid2",
                    pasientFnr = "pasientFnr"
            )
            database.lagreMottattSykmelding(copySykmeldingopplysning, copySykmeldingDokument)
            database.registerStatus(SykmeldingStatusEvent(copySykmeldingopplysning.id, copySykmeldingopplysning.mottattTidspunkt, StatusEvent.APEN, copySykmeldingopplysning.mottattTidspunkt.atOffset(ZoneOffset.UTC)))
            database.connection.opprettBehandlingsutfall(testBehandlingsutfall.copy(id = "uuid2"))

            val confirmedDateTime = LocalDateTime.now()
            val deletedStatus = SykmeldingStatusEvent("uuid", confirmedDateTime.plusHours(1), StatusEvent.SLETTET)
            database.registerStatus(deletedStatus)

            val sykmeldinger = sykmeldingService.hentSykmeldinger("pasientFnr")

            sykmeldinger.size shouldEqual 1
            sykmeldinger.first().id shouldEqual "uuid2"
        }

        it("Skal hente alle statuser") {
            database.registerStatus(SykmeldingStatusEvent("uuid", LocalDateTime.now().plusSeconds(10), StatusEvent.SENDT))
            val sykmeldingStatuser = sykmeldingStatusService.getSykmeldingStatus("uuid", null)
            sykmeldingStatuser.size shouldEqual 2
        }

        it("Skal hente siste status") {
            database.registerStatus(SykmeldingStatusEvent("uuid", LocalDateTime.now().plusSeconds(10), StatusEvent.SENDT))
            val sykmeldingstatuser = sykmeldingStatusService.getSykmeldingStatus("uuid", "LATEST")
            sykmeldingstatuser.size shouldEqual 1
            sykmeldingstatuser.get(0).event shouldEqual StatusEvent.SENDT
        }
    }
})
