package no.nav.syfo.sykmelding.internal.model

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.objectMapper
import org.amshove.kluent.`should equal`
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingMapperKtTest : Spek({

    val utdypendeopplysningerJson = "{\"6.2\":{\"6.2.1\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.2\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.3\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_NAV\"]},\"6.2.4\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]}}}"
    val mappedOpplysningerJson = "{\"6.2\":{\"6.2.1\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.2\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.4\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]}}}"
    describe("Test SykmeldingMapper") {
        it("Test map utdypendeOpplysninger") {
            val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>> = objectMapper.readValue(utdypendeopplysningerJson)
            val mappedMap = toUtdypendeOpplysninger(utdypendeOpplysninger)
            mappedOpplysningerJson `should equal` objectMapper.writeValueAsString(mappedMap)
        }
    }
})
