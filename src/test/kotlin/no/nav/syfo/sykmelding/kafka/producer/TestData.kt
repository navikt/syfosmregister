package no.nav.syfo.sykmelding.kafka.producer

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.KontaktMedPasientAGDTO
import no.nav.syfo.model.sykmelding.model.AdresseDTO
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.testutil.getNowTickMillisOffsetDateTime

fun getArbeidsgiverSykmelding(id: String): ArbeidsgiverSykmelding {
    return ArbeidsgiverSykmelding(
        id = id,
        kontaktMedPasient = KontaktMedPasientAGDTO(null),
        behandler = BehandlerAGDTO("fornavn", null, "etternavn", "hpr", AdresseDTO(null, null, null, null, null), null),
        behandletTidspunkt = getNowTickMillisOffsetDateTime(),
        prognose = null,
        syketilfelleStartDato = null,
        tiltakArbeidsplassen = null,
        meldingTilArbeidsgiver = null,
        arbeidsgiver = ArbeidsgiverAGDTO(null, null),
        mottattTidspunkt = getNowTickMillisOffsetDateTime(),
        sykmeldingsperioder = emptyList(),
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        papirsykmelding = false,
        merknader = null,
        utenlandskSykmelding = null,
        signaturDato = getNowTickMillisOffsetDateTime(),
    )
}

fun getKafkaMetadata(id: String): KafkaMetadataDTO {
    return KafkaMetadataDTO(
        sykmeldingId = id,
        timestamp = getNowTickMillisOffsetDateTime(),
        fnr = "fnr",
        source = "source",
    )
}

fun getSykmeldingStatusEvent(id: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = id,
        timestamp = getNowTickMillisOffsetDateTime(),
        arbeidsgiver = null,
        sporsmals = null,
        statusEvent = STATUS_BEKREFTET,
    )
}
