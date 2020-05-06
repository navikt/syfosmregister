package no.nav.syfo.sykmelding.kafka.producer

import java.time.OffsetDateTime
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.StatusEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.kafka.model.EnkelSykmelding
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO

fun getEnkelSykmelding(id: String): EnkelSykmelding {
    return EnkelSykmelding(
            id = id,
            kontaktMedPasient = KontaktMedPasientDTO(null, null),
            behandler = BehandlerDTO("fornavn", null, "etternavn", "aktorId", "fnr", null, null, AdresseDTO(null, null, null, null, null), null),
            behandletTidspunkt = OffsetDateTime.now(),
            prognose = null,
            syketilfelleStartDato = null,
            tiltakArbeidsplassen = null,
            navnFastlege = null,
            meldingTilArbeidsgiver = null,
            arbeidsgiver = ArbeidsgiverDTO(null, null),
            mottattTidspunkt = OffsetDateTime.now(),
            sykmeldingsperioder = emptyList(),
            legekontorOrgnummer = null,
            egenmeldt = false,
            harRedusertArbeidsgiverperiode = false,
            papirsykmelding = false
    )
}

fun getKafkaMetadata(id: String): KafkaMetadataDTO {
    return KafkaMetadataDTO(
            sykmeldingId = id,
            timestamp = OffsetDateTime.now(),
            fnr = "fnr",
            source = "source"
    )
}

fun getSykmeldingStatusEvent(id: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
            sykmeldingId = id,
            timestamp = OffsetDateTime.now(),
            arbeidsgiver = null,
            sporsmals = null,
            statusEvent = StatusEventDTO.BEKREFTET
    )
}
