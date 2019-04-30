package no.nav.syfo.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import java.sql.ResultSet
import java.time.LocalDateTime

data class BrukerSykmelding(
    val id: String,
    val bekreftetDato: LocalDateTime?,
    val behandlingsutfall: ValidationResult
)

fun brukerSykmeldingFromResultSet(resultSet: ResultSet): BrukerSykmelding {
    return BrukerSykmelding(
        resultSet.getString("id"),
        resultSet.getTimestamp("bekreftet_dato")?.toLocalDateTime(),
        objectMapper.readValue(resultSet.getString("behandlings_utfall"))
    )
}
