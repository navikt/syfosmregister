package no.nav.syfo.sykmelding.kafka.model

import java.time.LocalDate
import java.time.Month
import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.sykmelding.db.Periode

typealias MedisinskVurderingDB = no.nav.syfo.sykmelding.db.MedisinskVurdering
typealias DiagnoseDB = no.nav.syfo.sykmelding.db.Diagnose
typealias AnnenFraversArsakDB = no.nav.syfo.sykmelding.db.AnnenFraversArsak
typealias AnnenFraversGrunnDB = no.nav.syfo.sykmelding.db.AnnenFraverGrunn
private val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")
val koronaFraDato = LocalDate.of(2020, Month.MARCH, 15)
val koronaTilDato = LocalDate.of(2021, Month.OCTOBER, 1)

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
    if (fom.isAfter(koronaFraDato) || (fom.isBefore(koronaFraDato) && tom.isAfter(koronaFraDato))) {
        return fom.isBefore(koronaTilDato)
    }
    return false
}
