package no.nav.syfo.sykmelding.internal.db

import java.time.LocalDateTime
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult

data class SykmeldingDbModel(
    val id: String,
    val mottattTidspunkt: LocalDateTime,
    val legekontorOrgNr: String?,
    val behandlingsutfall: ValidationResult,
    val sykmeldingsDokument: Sykmelding,
    val status: String,
    val status_timestamp: LocalDateTime
)
