package no.nav.syfo.sykmelding.model

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
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

    val utdypendeopplysningerJson = "{\"6.2\":{\"6.2.1\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.2\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.3\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_NAV\"]},\"6.2.4\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_PASIENT\"]}}}"
    val mappedOpplysningerJson = "{\"6.2\":{\"6.2.1\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.2\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.4\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_PASIENT\"]}}}"
    val mappedeOpplysningerJsonPasient = "{\"6.2\":{\"6.2.1\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.2\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_ARBEIDSGIVER\"]},\"6.2.3\":{\"sporsmal\":\"spm\",\"svar\":\"svar\",\"restriksjoner\":[\"SKJERMET_FOR_NAV\"]}}}"

    describe("Test SykmeldingMapper") {
        it("Test map utdypendeOpplysninger - ikke pasient") {
            val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>> = objectMapper.readValue(utdypendeopplysningerJson)
            val mappedMap = toUtdypendeOpplysninger(utdypendeOpplysninger, false)
            mappedOpplysningerJson `should equal` objectMapper.writeValueAsString(mappedMap)
        }
        it("Test map utdypendeOpplysninger - pasient") {
            val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>> = objectMapper.readValue(utdypendeopplysningerJson)
            val mappedMap = toUtdypendeOpplysninger(utdypendeOpplysninger, true)
            mappedeOpplysningerJsonPasient `should equal` objectMapper.writeValueAsString(mappedMap)
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
        it("tilBehandlingsutfall fjerner regelinfo for manuell hvis pasient") {
            val validationResult = ValidationResult(Status.INVALID, listOf(
                RuleInfo("rulename", "sender", "user", Status.MANUAL_PROCESSING),
                RuleInfo("rulename2", "sender2", "user2", Status.INVALID))
            )

            val mappetBehandlingsutfall = validationResult.toBehandlingsutfallDTO(true)

            mappetBehandlingsutfall shouldEqual BehandlingsutfallDTO(RegelStatusDTO.INVALID, listOf(RegelinfoDTO("sender2", "user2", "rulename2", RegelStatusDTO.INVALID)))
        }
        it("tilBehandlingsutfall fjerner ikke regelinfo for manuell hvis ikke pasient") {
            val validationResult = ValidationResult(Status.INVALID, listOf(
                RuleInfo("rulename", "sender", "user", Status.MANUAL_PROCESSING),
                RuleInfo("rulename2", "sender2", "user2", Status.INVALID))
            )

            val mappetBehandlingsutfall = validationResult.toBehandlingsutfallDTO(false)

            mappetBehandlingsutfall shouldEqual BehandlingsutfallDTO(RegelStatusDTO.INVALID, listOf(
                RegelinfoDTO("sender", "user", "rulename", RegelStatusDTO.MANUAL_PROCESSING),
                RegelinfoDTO("sender2", "user2", "rulename2", RegelStatusDTO.INVALID)))
        }
    }

    describe("Test av skjerming") {
        it("Skal fjerne info hvis ikkeTilgangTilDiagnose er true") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedUtdypendeOpplysninger = sykmeldingDbModel.copy(
                sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(utdypendeOpplysninger = objectMapper.readValue(utdypendeopplysningerJson))
            )

            val mappetSykmelding = sykmeldingMedUtdypendeOpplysninger.toSykmeldingDTO(sporsmal = emptyList(), isPasient = false, ikkeTilgangTilDiagnose = true)

            mappetSykmelding.andreTiltak shouldEqual null
            mappetSykmelding.skjermesForPasient shouldEqual false
            mappetSykmelding.tiltakNAV shouldEqual null
            mappetSykmelding.medisinskVurdering shouldEqual null
            mappetSykmelding.meldingTilNAV shouldEqual null
            mappetSykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        }
        it("Skal fjerne info hvis ikkeTilgangTilDiagnose er true og pasient er skjermet") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(skjermet = true, perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedUtdypendeOpplysninger = sykmeldingDbModel.copy(
                sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(utdypendeOpplysninger = objectMapper.readValue(utdypendeopplysningerJson))
            )

            val mappetSykmelding = sykmeldingMedUtdypendeOpplysninger.toSykmeldingDTO(sporsmal = emptyList(), isPasient = false, ikkeTilgangTilDiagnose = true)

            mappetSykmelding.andreTiltak shouldEqual null
            mappetSykmelding.skjermesForPasient shouldEqual true
            mappetSykmelding.tiltakNAV shouldEqual null
            mappetSykmelding.medisinskVurdering shouldEqual null
            mappetSykmelding.meldingTilNAV shouldEqual null
            mappetSykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        }
        it("Skal fjerne info hvis pasient er skjermet og isPasient er true") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(skjermet = true, perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedUtdypendeOpplysninger = sykmeldingDbModel.copy(
                sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(utdypendeOpplysninger = objectMapper.readValue(utdypendeopplysningerJson))
            )

            val mappetSykmelding = sykmeldingMedUtdypendeOpplysninger.toSykmeldingDTO(sporsmal = emptyList(), isPasient = true, ikkeTilgangTilDiagnose = false)

            mappetSykmelding.andreTiltak shouldEqual null
            mappetSykmelding.skjermesForPasient shouldEqual true
            mappetSykmelding.tiltakNAV shouldEqual null
            mappetSykmelding.medisinskVurdering shouldEqual null
            mappetSykmelding.meldingTilNAV shouldEqual null
            mappetSykmelding.utdypendeOpplysninger shouldEqual emptyMap()
        }
        it("Skal ikke fjerne info hvis pasient er skjermet og isPasient er false") {
            val sykmeldingDbModel = getSykmeldingerDBmodel(skjermet = true, perioder = listOf(getPeriode(
                fom = LocalDate.of(2020, 3, 10),
                tom = LocalDate.of(2020, 3, 20)
            )))
            val sykmeldingMedUtdypendeOpplysninger = sykmeldingDbModel.copy(
                sykmeldingsDokument = sykmeldingDbModel.sykmeldingsDokument.copy(utdypendeOpplysninger = objectMapper.readValue(utdypendeopplysningerJson))
            )

            val mappetSykmelding = sykmeldingMedUtdypendeOpplysninger.toSykmeldingDTO(sporsmal = emptyList(), isPasient = false, ikkeTilgangTilDiagnose = false)

            mappetSykmelding.andreTiltak shouldEqual "Andre tiltak"
            mappetSykmelding.skjermesForPasient shouldEqual true
            mappetSykmelding.tiltakNAV shouldEqual "Tiltak NAV"
            mappetSykmelding.medisinskVurdering shouldEqual MedisinskVurderingDTO(hovedDiagnose = DiagnoseDTO("L87", "ICPC-2", "Bursitt/tendinitt/synovitt IKA"), biDiagnoser = emptyList(), annenFraversArsak = null, svangerskap = false, yrkesskade = false, yrkesskadeDato = null)
            mappetSykmelding.meldingTilNAV shouldEqual MeldingTilNavDTO(true, "Masse bistand")
            mappetSykmelding.utdypendeOpplysninger shouldEqual objectMapper.readValue(mappedOpplysningerJson)
        }
    }
})
