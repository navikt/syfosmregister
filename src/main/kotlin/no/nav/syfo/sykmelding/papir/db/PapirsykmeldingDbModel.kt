package no.nav.syfo.sykmelding.papir.db

import java.time.OffsetDateTime
import no.nav.syfo.sykmelding.db.Sykmelding
import no.nav.syfo.sykmelding.papir.model.PapirsykmeldingDTO

data class PapirsykmeldingDbModel(
    val pasientFnr: String,
    val mottattTidspunkt: OffsetDateTime,
    val sykmelding: Sykmelding,
)

fun PapirsykmeldingDbModel.toPapirsykmeldingDTO(): PapirsykmeldingDTO {
    return PapirsykmeldingDTO(
        pasientFnr = this.pasientFnr,
        mottattTidspunkt = this.mottattTidspunkt,
        sykmelding = this.sykmelding,
    )
}
