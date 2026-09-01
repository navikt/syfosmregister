package no.nav.syfo.persistering

import no.nav.syfo.jsonMapper
import no.nav.syfo.model.ValidationResult
import org.postgresql.util.PGobject

data class Behandlingsutfall(val id: String, val behandlingsutfall: ValidationResult)

fun ValidationResult.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = jsonMapper.writeValueAsString(this)
    }
