package no.nav.syfo.model

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.jsonMapper
import org.amshove.kluent.shouldBeEqualTo
import tools.jackson.module.kotlin.readValue

class UtenlandskSykmeldingTest :
    FunSpec({
        test("Test at folkeRegistertAdresseErBrakkeEllerTilsvarende blir false, dersom den ikkje er med") {
            val sykmelding = jsonMapper.readValue<UtenlandskSykmelding>("""{"land":"SWE"}""")

            sykmelding.folkeRegistertAdresseErBrakkeEllerTilsvarende shouldBeEqualTo false
        }
    })
