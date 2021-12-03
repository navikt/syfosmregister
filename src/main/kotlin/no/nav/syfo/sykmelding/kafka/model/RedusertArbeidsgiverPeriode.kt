package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.sykmelding.db.Periode
import java.time.LocalDate
import java.time.Month

typealias MedisinskVurderingDB = no.nav.syfo.sykmelding.db.MedisinskVurdering
typealias DiagnoseDB = no.nav.syfo.sykmelding.db.Diagnose
typealias AnnenFraversArsakDB = no.nav.syfo.sykmelding.db.AnnenFraversArsak
typealias AnnenFraversGrunnDB = no.nav.syfo.sykmelding.db.AnnenFraverGrunn
private val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")
val koronaForsteFraDato = LocalDate.of(2020, Month.MARCH, 15)
val koronaForsteTilDato = LocalDate.of(2021, Month.OCTOBER, 1)
val koronaAndreFraDato = LocalDate.of(2021, Month.DECEMBER, 5) // Må bekreftes at reglene gjelder fra og med 6/12!

fun MedisinskVurderingDB.getHarRedusertArbeidsgiverperiode(sykmeldingsperioder: List<Periode>): Boolean {
    val sykmeldingsperioderInnenforKoronaregler = sykmeldingsperioder.filter { periodeErInnenforKoronaregler(it.fom, it.tom) }
    if (sykmeldingsperioderInnenforKoronaregler.isEmpty()) {
        return false
    }
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurderingDB.checkSmittefare() =
    annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == no.nav.syfo.sykmelding.db.AnnenFraverGrunn.SMITTEFARE } == true

fun MedisinskVurdering.getHarRedusertArbeidsgiverperiode(sykmeldingsperioder: List<no.nav.syfo.model.Periode>): Boolean {
    val sykmeldingsperioderInnenforKoronaregler = sykmeldingsperioder.filter { periodeErInnenforKoronaregler(it.fom, it.tom) }
    if (sykmeldingsperioderInnenforKoronaregler.isEmpty()) {
        return false
    }
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose!!.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurdering.checkSmittefare() =
    annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == AnnenFraverGrunn.SMITTEFARE } == true

fun periodeErInnenforKoronaregler(fom: LocalDate, tom: LocalDate): Boolean {
    if (fom.isAfter(koronaAndreFraDato) || (fom.isBefore(koronaAndreFraDato) && tom.isAfter(koronaAndreFraDato))) {
        return true
    } else if (fom.isAfter(koronaForsteFraDato) || (fom.isBefore(koronaForsteFraDato) && tom.isAfter(koronaForsteFraDato))) {
        return fom.isBefore(koronaForsteTilDato)
    }
    return false
}
