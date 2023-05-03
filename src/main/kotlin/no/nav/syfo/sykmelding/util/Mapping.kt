package no.nav.syfo.sykmelding.util

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.Sykmeldingsopplysninger

fun mapToSykmeldingsopplysninger(receivedSykmelding: ReceivedSykmelding) =
    Sykmeldingsopplysninger(
        id = receivedSykmelding.sykmelding.id,
        pasientFnr = receivedSykmelding.personNrPasient,
        pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
        legeFnr = receivedSykmelding.personNrLege,
        legeHpr = receivedSykmelding.legeHprNr,
        legeHelsepersonellkategori = receivedSykmelding.legeHelsepersonellkategori,
        legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
        mottakId = receivedSykmelding.navLogId,
        legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
        legekontorHerId = receivedSykmelding.legekontorHerId,
        legekontorReshId = receivedSykmelding.legekontorReshId,
        epjSystemNavn = receivedSykmelding.sykmelding.avsenderSystem.navn,
        epjSystemVersjon = receivedSykmelding.sykmelding.avsenderSystem.versjon,
        mottattTidspunkt = receivedSykmelding.mottattDato,
        tssid = receivedSykmelding.tssid,
        merknader = receivedSykmelding.merknader,
        partnerreferanse = receivedSykmelding.partnerreferanse,
        utenlandskSykmelding = receivedSykmelding.utenlandskSykmelding,
    )
