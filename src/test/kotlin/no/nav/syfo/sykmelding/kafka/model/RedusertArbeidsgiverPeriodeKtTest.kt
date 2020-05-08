package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.model.AnnenFraversArsak
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.MedisinskVurdering
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
typealias MedisinskVurderingDB = no.nav.syfo.sykmelding.db.MedisinskVurdering
typealias DiagnoseDB = no.nav.syfo.sykmelding.db.Diagnose
typealias AnnenFraversArsakDB = no.nav.syfo.sykmelding.db.AnnenFraversArsak
typealias AnnenFraversGrunnDB = no.nav.syfo.sykmelding.db.AnnenFraverGrunn

class RedusertArbeidsgiverPeriodeKtTest : Spek({
    describe("Test har redusertArbeidsgiverperiode no.nav.syfo.model.MedisinskVurdering") {
        it("Should not get redusert arbeidsgiverperiode") {
            val diagnose = getMedisinskVurdering(diagnoseKode = "123", bidiagnoseKode = "123")
            val redusert = diagnose.getHarRedusertArbeidsgiverperiode()
            redusert shouldBe false
        }
        it("Should get redusert arbeidsgiverperiode for hoveddiagnose") {
            getMedisinskVurdering(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(diagnoseKode = "U071").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(diagnoseKode = "U072").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(diagnoseKode = "A23").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(diagnoseKode = "R992").getHarRedusertArbeidsgiverperiode() shouldBe true
        }
        it("Should get redusert arbeidsgiverperiode for bidiagnoser") {
            getMedisinskVurdering(bidiagnoseKode = "R991").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "U071").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "U072").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "A23").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "R992").getHarRedusertArbeidsgiverperiode() shouldBe true
        }
        it("Should not get redusert arbeidsgiverperiode when not smittefare") {
            getMedisinskVurdering(annenFraversArsak = AnnenFraversArsak("beskrivelse", listOf(AnnenFraverGrunn.ARBEIDSRETTET_TILTAK)))
                    .getHarRedusertArbeidsgiverperiode() shouldBe false
        }

        it("Should get redusert arbeidsgiverperiode ved smittefare") {
            getMedisinskVurdering(annenFraversArsak = AnnenFraversArsak("beskrivelse", listOf(AnnenFraverGrunn.SMITTEFARE)))
                    .getHarRedusertArbeidsgiverperiode() shouldBe true
        }
    }

    describe("Test har redusertArbeidsgiverperiode no.nav.syfo.model.MedisinskVurdering") {
        it("Should not get redusert arbeidsgiverperiode") {
            val diagnose = getMedisinskVurderingDB(diagnoseKode = "123", bidiagnoseKode = "123")
            val redusert = diagnose.getHarRedusertArbeidsgiverperiode()
            redusert shouldBe false
        }
        it("Should get redusert arbeidsgiverperiode for hoveddiagnose") {
            getMedisinskVurderingDB(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "U071").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "U072").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "A23").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "R992").getHarRedusertArbeidsgiverperiode() shouldBe true
        }
        it("Should get redusert arbeidsgiverperiode for bidiagnoser") {
            getMedisinskVurderingDB(bidiagnoseKode = "R991").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "U071").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "U072").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "A23").getHarRedusertArbeidsgiverperiode() shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "R992").getHarRedusertArbeidsgiverperiode() shouldBe true
        }
        it("Should not get redusert arbeidsgiverperiode when not smittefare") {
            getMedisinskVurderingDB(annenFraversArsak = AnnenFraversArsakDB("beskrivelse", listOf(AnnenFraversGrunnDB.ARBEIDSRETTET_TILTAK)))
                    .getHarRedusertArbeidsgiverperiode() shouldBe false
        }

        it("Should get redusert arbeidsgiverperiode ved smittefare") {
            getMedisinskVurderingDB(annenFraversArsak = AnnenFraversArsakDB("beskrivelse", listOf(AnnenFraversGrunnDB.SMITTEFARE)))
                    .getHarRedusertArbeidsgiverperiode() shouldBe true
        }
    }
})

private fun getMedisinskVurdering(diagnoseKode: String? = null, bidiagnoseKode: String? = null, annenFraversArsak: AnnenFraversArsak? = null): MedisinskVurdering {
    val diagnose = diagnoseKode?.let { Diagnose("system", diagnoseKode, "tekst") }
    val bidiagnose = mutableListOf<Diagnose>()

    bidiagnoseKode?.run {
        bidiagnose.add(Diagnose("system", bidiagnoseKode, "tekst"))
    }

    return MedisinskVurdering(
            diagnose,
            bidiagnose,
            false,
            false,
            null,
            annenFraversArsak
    )
}

private fun getMedisinskVurderingDB(diagnoseKode: String? = null, bidiagnoseKode: String? = null, annenFraversArsak: AnnenFraversArsakDB? = null): MedisinskVurderingDB {
    val diagnose = diagnoseKode?.let { DiagnoseDB("system", diagnoseKode, "tekst") }
    val bidiagnose = mutableListOf<DiagnoseDB>()

    bidiagnoseKode?.run {
        bidiagnose.add(DiagnoseDB("system", bidiagnoseKode, "tekst"))
    }

    return MedisinskVurderingDB(
            diagnose,
            bidiagnose,
            false,
            false,
            null,
            annenFraversArsak
    )
}
