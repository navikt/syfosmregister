package no.nav.syfo.sykmelding.kafka.model

import no.nav.syfo.model.AnnenFraverGrunn
import no.nav.syfo.sykmelding.db.MedisinskVurdering

private val diagnoserSomGirRedusertArbgiverPeriode = listOf("R991", "U071", "U072", "A23", "R992")

fun MedisinskVurdering.getHarRedusertArbeidsgiverperiode(): Boolean {
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun MedisinskVurdering.checkSmittefare() =
        annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == no.nav.syfo.sykmelding.db.AnnenFraverGrunn.SMITTEFARE } == true

fun no.nav.syfo.model.MedisinskVurdering.getHarRedusertArbeidsgiverperiode(): Boolean {
    if (hovedDiagnose != null && diagnoserSomGirRedusertArbgiverPeriode.contains(hovedDiagnose!!.kode)) {
        return true
    } else if (!biDiagnoser.isNullOrEmpty() && biDiagnoser.find { diagnoserSomGirRedusertArbgiverPeriode.contains(it.kode) } != null) {
        return true
    }
    return checkSmittefare()
}

private fun no.nav.syfo.model.MedisinskVurdering.checkSmittefare() =
        annenFraversArsak?.grunn?.any { annenFraverGrunn -> annenFraverGrunn == AnnenFraverGrunn.SMITTEFARE } == true
