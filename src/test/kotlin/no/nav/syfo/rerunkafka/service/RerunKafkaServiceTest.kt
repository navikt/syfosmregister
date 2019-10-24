package no.nav.syfo.rerunkafka.service

import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.rerunkafka.database.getSykmeldingerByIds
import no.nav.syfo.rerunkafka.kafka.RerunKafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.UUID

class RerunKafkaServiceTest : Spek({
    val sykmeldingServic = mockkClass(DatabaseInterface::class)
    val rerunKafkaProducer = mockkClass(RerunKafkaProducer::class)
    mockkStatic("no.nav.syfo.rerunkafka.database.SykmeldingAksesseringQueriesKt")
    val sykmeldingIds = 0.until(10).map { UUID.randomUUID().toString() }
    every { rerunKafkaProducer.publishToKafka(any()) } returns Unit
    every { sykmeldingServic.getSykmeldingerByIds(any()) } returns sykmeldingIds.map { toReceivedSykmelding(it) }

    describe("Publish sykmeldinger to kafka topic automatiskBehandling") {
        it("Should get from database and publish to kafka") {
            val kafkaService = RerunKafkaService(sykmeldingServic, rerunKafkaProducer)
            kafkaService.rerun(sykmeldingIds)
            verify(exactly = 10) { rerunKafkaProducer.publishToKafka(any()) }
        }
    }
})

fun toReceivedSykmelding(it: String): ReceivedSykmelding {
    return ReceivedSykmelding(
            sykmelding = Sykmelding(id = it,
                    msgId = UUID.randomUUID().toString(),
                    kontaktMedPasient = KontaktMedPasient(null, null),
                    perioder = emptyList(),
                    andreTiltak = null,
                    arbeidsgiver = Arbeidsgiver(harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER, navn = "", stillingsprosent = 100, yrkesbetegnelse = ""),
                    avsenderSystem = AvsenderSystem("", ""),
                    behandler = Behandler(fornavn = "", adresse = Adresse(null, null, null, null, null),
                            aktoerId = "", etternavn = "", fnr = "", her = "", hpr = "", mellomnavn = "", tlf = ""),
                    behandletTidspunkt = LocalDateTime.now(),
                    medisinskVurdering = MedisinskVurdering(null, emptyList(), false, false, null, null),
                    meldingTilArbeidsgiver = null,
                    meldingTilNAV = null,
                    navnFastlege = null,
                    pasientAktoerId = "",
                    prognose = Prognose(false, null, null, null),
                    signaturDato = LocalDateTime.now(),
                    skjermesForPasient = false,
                    syketilfelleStartDato = null,
                    tiltakArbeidsplassen = null,
                    tiltakNAV = null,
                    utdypendeOpplysninger = emptyMap()),
            msgId = "",
            navLogId = "",
            tlfPasient = "",
            legekontorOrgName = "",
            fellesformat = "",
            legekontorHerId = null,
            legekontorOrgNr = "",
            legekontorReshId = "",
            mottattDato = LocalDateTime.now(),
            personNrLege = "",
            personNrPasient = "",
            tssid = "",
            rulesetVersion = ""
    )
}
