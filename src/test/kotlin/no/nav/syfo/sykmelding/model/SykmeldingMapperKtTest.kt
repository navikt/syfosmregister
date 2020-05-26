package no.nav.syfo.sykmelding.model

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.db.AnnenFraverGrunn
import no.nav.syfo.sykmelding.db.AnnenFraversArsak
import no.nav.syfo.sykmelding.db.SporsmalSvar
import no.nav.syfo.testutil.getPeriode
import no.nav.syfo.testutil.getSykmeldingerDBmodel
import org.amshove.kluent.`should equal`
import org.amshove.kluent.shouldEqual
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
        it("test map har ikke redusert arbeidsgiverperiode") {
            val sykmeldingDto = getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            ))).toSykmeldingDTO(sporsmal = emptyList(), ikkeTilgangTilDiagnose = false)
            sykmeldingDto.harRedusertArbeidsgiverperiode shouldEqual false
        }
        it("test map har redusert arbeidsgiverperiode ved smittefare") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedSmittefare = sykmeldingDbModel.copy(
                    sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(
                            medisinskVurdering = sykmeldingDbModel.sykmeldingsDokument.medisinskVurdering.copy(
                                    annenFraversArsak = AnnenFraversArsak(null, listOf(
                                            AnnenFraverGrunn.SMITTEFARE
                                    ))
                            )
                    )
            )

            val sykmeldingDto = sykmeldingMedSmittefare.toSykmeldingDTO(sporsmal = emptyList(), ikkeTilgangTilDiagnose = false)
            sykmeldingDto.harRedusertArbeidsgiverperiode shouldEqual true
        }

        it("test map har ikke redusert arbeidsgiverperiode ved annen fravarsgrunn ikke smittefare") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedSmittefare = sykmeldingDbModel.copy(
                    sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(
                            medisinskVurdering = sykmeldingDbModel.sykmeldingsDokument.medisinskVurdering.copy(
                                    annenFraversArsak = AnnenFraversArsak(null, listOf(
                                            AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID
                                    ))
                            )
                    )
            )

            val sykmeldingDto = sykmeldingMedSmittefare.toSykmeldingDTO(sporsmal = emptyList(), ikkeTilgangTilDiagnose = false)
            sykmeldingDto.harRedusertArbeidsgiverperiode shouldEqual false
        }
    }
})
