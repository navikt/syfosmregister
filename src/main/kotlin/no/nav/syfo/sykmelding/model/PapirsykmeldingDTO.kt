package no.nav.syfo.sykmelding.model

import no.nav.syfo.sykmelding.db.Sykmelding
import java.time.OffsetDateTime

data class PapirsykmeldingDTO(
    val pasientFnr: String,
    val pasientAktoerId: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)
