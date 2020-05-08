package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.model.MedisinskVurdering

typealias MedisinskVurderingDB = no.nav.syfo.sykmelding.db.MedisinskVurdering
typealias DiagnoseDB = no.nav.syfo.sykmelding.db.Diagnose
typealias AnnenFraversArsakDB = no.nav.syfo.sykmelding.db.AnnenFraversArsak
typealias AnnenFraversGrunnDB = no.nav.syfo.sykmelding.db.AnnenFraverGrunn
private val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")

fun MedisinskVurderingDB.getHarRedusertArbeidsgiverperiode(): Boolean {
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurderingDB.checkSmittefare() =
        annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == no.nav.syfo.sykmelding.db.AnnenFraverGrunn.SMITTEFARE } == true

fun MedisinskVurdering.getHarRedusertArbeidsgiverperiode(): Boolean {
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose!!.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurdering.checkSmittefare() =
        annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == AnnenFraverGrunn.SMITTEFARE } == true
