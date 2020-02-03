package no.nav.syfo.sykmelding.internal.db

import java.time.LocalDateTime
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult

data class ArbeidsgiverDbModel(
    val orgNr: String,
    val juridiskOrgNr: String,
    val navn: String
)

data class StatusDbModel(
    val status: String,
    val status_timestamp: LocalDateTime,
    val arbeidsgiver: ArbeidsgiverDbModel?
)

data class SykmeldingDbModel(
    val id: String,
    val mottattTidspunkt: LocalDateTime,
    val legekontorOrgNr: String?,
    val behandlingsutfall: ValidationResult,
    val sykmeldingsDokument: Sykmelding,
    val status: StatusDbModel
)
