package no.nav.syfo.sykmelding.papir.db

import no.nav.syfo.sykmelding.db.Sykmelding
import no.nav.syfo.sykmelding.papir.model.PapirsykmeldingDTO
import java.time.OffsetDateTime

data class PapirsykmeldingDbModel(
    val pasientFnr: String,
    val pasientAktoerId: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)

fun PapirsykmeldingDbModel.toPapirsykmeldingDTO(): PapirsykmeldingDTO {
    return PapirsykmeldingDTO(
        pasientFnr = this.pasientFnr,
        pasientAktoerId = this.pasientAktoerId,
        mottattTidspunkt = this.mottattTidspunkt,
        sykmelding = this.sykmelding
    )
}
