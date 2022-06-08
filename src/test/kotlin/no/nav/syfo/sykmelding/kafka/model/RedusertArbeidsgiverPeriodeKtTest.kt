package no.nav.syfo.sykmelding.kafka.model

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.model.AnnenFraversArsak
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import org.amshove.kluent.shouldBe
import java.time.LocalDate
import java.time.Month

typealias MedisinskVurderingDB = no.nav.syfo.sykmelding.db.MedisinskVurdering
typealias DiagnoseDB = no.nav.syfo.sykmelding.db.Diagnose
typealias AnnenFraversArsakDB = no.nav.syfo.sykmelding.db.AnnenFraversArsak
typealias AnnenFraversGrunnDB = no.nav.syfo.sykmelding.db.AnnenFraverGrunn

class RedusertArbeidsgiverPeriodeKtTest : FunSpec({
    val periodeInnenforKoronaregler = listOf<Periode>(
        Periode(
            fom = koronaForsteFraDato.plusDays(1),
            tom = koronaForsteFraDato.plusDays(15),
            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    val periodeUtenforKoronaregler = listOf<Periode>(
        Periode(
            fom = koronaForsteFraDato.minusDays(50),
            tom = koronaForsteFraDato.minusDays(30),
            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    val perioderUtenforOgInnenforKoronaregler = listOf<Periode>(
        Periode(
            fom = koronaForsteFraDato.minusDays(50),
            tom = koronaForsteFraDato.minusDays(30),
            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        ),
        Periode(
            fom = koronaForsteFraDato.plusDays(1),
            tom = koronaForsteFraDato.plusDays(15),
            aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    val dbPeriodeInnenforKoronaregler = listOf<no.nav.syfo.sykmelding.db.Periode>(
        no.nav.syfo.sykmelding.db.Periode(
            fom = koronaForsteFraDato.plusDays(1),
            tom = koronaForsteFraDato.plusDays(15),
            aktivitetIkkeMulig = no.nav.syfo.sykmelding.db.AktivitetIkkeMulig(medisinskArsak = no.nav.syfo.sykmelding.db.MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    val dbPeriodeUtenforKoronaregler = listOf<no.nav.syfo.sykmelding.db.Periode>(
        no.nav.syfo.sykmelding.db.Periode(
            fom = koronaForsteFraDato.minusDays(50),
            tom = koronaForsteFraDato.minusDays(30),
            aktivitetIkkeMulig = no.nav.syfo.sykmelding.db.AktivitetIkkeMulig(medisinskArsak = no.nav.syfo.sykmelding.db.MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    val dbPerioderUtenforOgInnenforKoronaregler = listOf<no.nav.syfo.sykmelding.db.Periode>(
        no.nav.syfo.sykmelding.db.Periode(
            fom = koronaForsteFraDato.minusDays(50),
            tom = koronaForsteFraDato.minusDays(30),
            aktivitetIkkeMulig = no.nav.syfo.sykmelding.db.AktivitetIkkeMulig(medisinskArsak = no.nav.syfo.sykmelding.db.MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        ),
        no.nav.syfo.sykmelding.db.Periode(
            fom = koronaForsteFraDato.plusDays(1),
            tom = koronaForsteFraDato.plusDays(15),
            aktivitetIkkeMulig = no.nav.syfo.sykmelding.db.AktivitetIkkeMulig(medisinskArsak = no.nav.syfo.sykmelding.db.MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
            gradert = null,
            behandlingsdager = null,
            reisetilskudd = false,
            avventendeInnspillTilArbeidsgiver = null
        )
    )
    context("Test har redusertArbeidsgiverperiode no.nav.syfo.model.MedisinskVurdering") {
        test("Should not get redusert arbeidsgiverperiode") {
            val diagnose = getMedisinskVurdering(diagnoseKode = "123", bidiagnoseKode = "123")
            val redusert = diagnose.getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler)
            redusert shouldBe false
        }
        test("Should get redusert arbeidsgiverperiode for hoveddiagnose") {
            getMedisinskVurdering(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(diagnoseKode = "U071").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(diagnoseKode = "U072").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(diagnoseKode = "A23").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(diagnoseKode = "R992").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
        }
        test("Should get redusert arbeidsgiverperiode for bidiagnoser") {
            getMedisinskVurdering(bidiagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "U071").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "U072").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "A23").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
            getMedisinskVurdering(bidiagnoseKode = "R992").getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
        }
        test("Should not get redusert arbeidsgiverperiode when not smittefare") {
            getMedisinskVurdering(annenFraversArsak = AnnenFraversArsak("beskrivelse", listOf(AnnenFraverGrunn.ARBEIDSRETTET_TILTAK)))
                .getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe false
        }

        test("Should get redusert arbeidsgiverperiode ved smittefare") {
            getMedisinskVurdering(annenFraversArsak = AnnenFraversArsak("beskrivelse", listOf(AnnenFraverGrunn.SMITTEFARE)))
                .getHarRedusertArbeidsgiverperiode(periodeInnenforKoronaregler) shouldBe true
        }
        test("skal ikke gi redusert arbeidsgiverperiode hvis periode er før koronareglene gjelder") {
            getMedisinskVurdering(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(periodeUtenforKoronaregler) shouldBe false
        }
        test("skal gi redusert arbeidsgiverperiode hvis en av periodene er etter at koronareglene gjelder") {
            getMedisinskVurdering(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(perioderUtenforOgInnenforKoronaregler) shouldBe true
        }
    }

    context("Test har redusertArbeidsgiverperiode no.nav.syfo.sykmelding.db.MedisinskVurdering") {
        test("Should not get redusert arbeidsgiverperiode") {
            val diagnose = getMedisinskVurderingDB(diagnoseKode = "123", bidiagnoseKode = "123")
            val redusert = diagnose.getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler)
            redusert shouldBe false
        }
        test("Should get redusert arbeidsgiverperiode for hoveddiagnose") {
            getMedisinskVurderingDB(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "U071").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "U072").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "A23").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(diagnoseKode = "R992").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
        }
        test("Should get redusert arbeidsgiverperiode for bidiagnoser") {
            getMedisinskVurderingDB(bidiagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "U071").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "U072").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "A23").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
            getMedisinskVurderingDB(bidiagnoseKode = "R992").getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
        }
        test("Should not get redusert arbeidsgiverperiode when not smittefare") {
            getMedisinskVurderingDB(annenFraversArsak = AnnenFraversArsakDB("beskrivelse", listOf(AnnenFraversGrunnDB.ARBEIDSRETTET_TILTAK)))
                .getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe false
        }

        test("Should get redusert arbeidsgiverperiode ved smittefare") {
            getMedisinskVurderingDB(annenFraversArsak = AnnenFraversArsakDB("beskrivelse", listOf(AnnenFraversGrunnDB.SMITTEFARE)))
                .getHarRedusertArbeidsgiverperiode(dbPeriodeInnenforKoronaregler) shouldBe true
        }
        test("skal ikke gi redusert arbeidsgiverperiode hvis periode er før koronareglene gjelder") {
            getMedisinskVurderingDB(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(dbPeriodeUtenforKoronaregler) shouldBe false
        }
        test("skal gi redusert arbeidsgiverperiode hvis en av periodene er etter at koronareglene gjelder") {
            getMedisinskVurderingDB(diagnoseKode = "R991").getHarRedusertArbeidsgiverperiode(dbPerioderUtenforOgInnenforKoronaregler) shouldBe true
        }
    }

    context("Test av datologikk i periodeErInnenforKoronaregler") {
        test("FOM = 16. mars 2020 skal gi true") {
            val fom = LocalDate.of(2020, Month.MARCH, 16)
            val tom = LocalDate.of(2020, Month.APRIL, 1)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe true
        }
        test("FOM = 1. mars 2020 og TOM = 20. mars skal gi true") {
            val fom = LocalDate.of(2020, Month.MARCH, 1)
            val tom = LocalDate.of(2020, Month.MARCH, 20)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe true
        }
        test("FOM = 1. mars 2020 og TOM = 15. mars skal gi false") {
            val fom = LocalDate.of(2020, Month.MARCH, 1)
            val tom = LocalDate.of(2020, Month.MARCH, 15)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe false
        }

        test("FOM = 1. oktober 2021 og TOM = 15. oktober skal gi false") {
            val fom = LocalDate.of(2021, Month.OCTOBER, 1)
            val tom = LocalDate.of(2021, Month.OCTOBER, 15)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe false
        }

        test("FOM = 30. september 2021 og TOM = 15. oktober skal gi true") {
            val fom = LocalDate.of(2021, Month.SEPTEMBER, 30)
            val tom = LocalDate.of(2021, Month.OCTOBER, 15)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe true
        }

        test("FOM = 1. desember 2021 og TOM = 15. desember skal gi true") {
            val fom = LocalDate.of(2021, Month.DECEMBER, 1)
            val tom = LocalDate.of(2021, Month.DECEMBER, 15)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe true
        }

        test("FOM = 26. november 2021 og TOM = 5. desember skal gi false") {
            val fom = LocalDate.of(2021, Month.NOVEMBER, 26)
            val tom = LocalDate.of(2021, Month.DECEMBER, 5)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe false
        }

        test("FOM = 25. november 2021 og TOM = 30. november skal gi false") {
            val fom = LocalDate.of(2021, Month.NOVEMBER, 25)
            val tom = LocalDate.of(2021, Month.NOVEMBER, 30)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe false
        }

        test("FOM = 25. juni 2022 og TOM = 2. juli skal gi true") {
            val fom = LocalDate.of(2022, Month.JUNE, 25)
            val tom = LocalDate.of(2022, Month.JULY, 2)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe true
        }

        test("FOM = 1. juli 2022 og TOM = 8. juli skal gi false") {
            val fom = LocalDate.of(2022, Month.JULY, 1)
            val tom = LocalDate.of(2022, Month.JULY, 8)

            val innenforKoronaPeriode = periodeErInnenforKoronaregler(fom, tom)

            innenforKoronaPeriode shouldBe false
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
