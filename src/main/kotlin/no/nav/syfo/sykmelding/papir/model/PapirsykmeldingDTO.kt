package no.nav.syfo.sykmelding.papir.model

import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.db.Sykmelding

data class PapirsykmeldingDTO(
    val pasientFnr: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)
