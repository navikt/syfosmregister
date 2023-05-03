package no.nav.syfo.testutil

import no.nav.syfo.Environment
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sm.Diagnosekoder
import no.nav.syfo.sykmelding.db.Adresse
import no.nav.syfo.sykmelding.db.AktivitetIkkeMulig
import no.nav.syfo.sykmelding.db.Arbeidsgiver
import no.nav.syfo.sykmelding.db.AvsenderSystem
import no.nav.syfo.sykmelding.db.Behandler
import no.nav.syfo.sykmelding.db.Diagnose
import no.nav.syfo.sykmelding.db.Gradert
import no.nav.syfo.sykmelding.db.HarArbeidsgiver
import no.nav.syfo.sykmelding.db.KontaktMedPasient
import no.nav.syfo.sykmelding.db.MedisinskArsak
import no.nav.syfo.sykmelding.db.MedisinskVurdering
import no.nav.syfo.sykmelding.db.MeldingTilNAV
import no.nav.syfo.sykmelding.db.Periode
import no.nav.syfo.sykmelding.db.StatusDbModel
import no.nav.syfo.sykmelding.db.Sykmelding
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AnnenFraversArsakDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
import no.nav.syfo.sykmelding.model.GradertDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.papir.db.PapirsykmeldingDbModel
import java.time.LocalDate
import java.time.OffsetDateTime

fun getEnvironment(): Environment {
    return Environment(
        cluster = "",
        syfoTilgangskontrollUrl = "",
        clientIdV2 = "clientid",
        clientSecretV2 = "",
        jwkKeysUrlV2 = "",
        jwtIssuerV2 = "assureissuer",
        syfotilgangskontrollScope = "",
        azureTokenEndpoint = "",
        pdlGraphqlPath = "",
        pdlScope = "scope",
        tokenXWellKnownUrl = "",
        clientIdTokenX = "clientid",
        dbName = "database",
        databasePassword = "password",
        databaseUsername = "username",
        dbHost = "localhost",
        dbPort = "5432",
        schemaRegistryUrl = "schema",
        kafkaSchemaRegistryUsername = "usr",
        kafkaSchemaRegistryPassword = "pwd",
        pdlAktorV2Topic = "aktorV2",
        electorPath = "leader",
    )
}

fun getSykmeldingDto(perioder: List<SykmeldingsperiodeDTO> = getPerioder()): SykmeldingDTO {
    return SykmeldingDTO(
        id = "1",
        utdypendeOpplysninger = emptyMap(),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        sykmeldingsperioder = perioder,
        sykmeldingStatus = no.nav.syfo.sykmelding.model.SykmeldingStatusDTO("APEN", getNowTickMillisOffsetDateTime(), null, emptyList()),
        behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
        medisinskVurdering = getMedisinskVurdering(),
        behandler = BehandlerDTO(
            "fornavn", null, "etternavn",
            "123", "01234567891", null, null,
            AdresseDTO(null, null, null, null, null), null,
        ),
        behandletTidspunkt = getNowTickMillisOffsetDateTime(),
        mottattTidspunkt = getNowTickMillisOffsetDateTime(),
        skjermesForPasient = false,
        meldingTilNAV = null,
        prognose = null,
        arbeidsgiver = null,
        tiltakNAV = null,
        syketilfelleStartDato = null,
        tiltakArbeidsplassen = null,
        navnFastlege = null,
        meldingTilArbeidsgiver = null,
        legekontorOrgnummer = null,
        andreTiltak = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        papirsykmelding = false,
        merknader = null,
        utenlandskSykmelding = null,
    )
}

fun getMedisinskVurdering(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
        hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
        biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose")),
        annenFraversArsak = AnnenFraversArsakDTO("", emptyList()),
        svangerskap = false,
        yrkesskade = false,
        yrkesskadeDato = null,
    )
}

fun getPerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), null, null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false))
}

fun getGradertePerioder(): List<SykmeldingsperiodeDTO> {
    return listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), GradertDTO(50, false), null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false))
}

fun getSykmeldingerDBmodel(skjermet: Boolean = false, perioder: List<Periode> = emptyList()): SykmeldingDbModel {
    return SykmeldingDbModel(
        id = "123",
        behandlingsutfall = ValidationResult(Status.OK, emptyList()),
        mottattTidspunkt = getNowTickMillisOffsetDateTime(),
        status = StatusDbModel(
            statusEvent = "APEN",
            arbeidsgiver = null,
            statusTimestamp = getNowTickMillisOffsetDateTime(),
        ),
        legekontorOrgNr = "123456789",
        sykmeldingsDokument = Sykmelding(
            id = "123",
            arbeidsgiver = Arbeidsgiver(
                harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                navn = "navn",
                stillingsprosent = null,
                yrkesbetegnelse = null,
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(Diagnosekoder.ICPC2_CODE, "L87", null),
                biDiagnoser = emptyList(),
                yrkesskade = false,
                svangerskap = false,
                annenFraversArsak = null,
                yrkesskadeDato = null,
            ),
            andreTiltak = "Andre tiltak",
            meldingTilArbeidsgiver = null,
            navnFastlege = null,
            tiltakArbeidsplassen = null,
            syketilfelleStartDato = null,
            tiltakNAV = "Tiltak NAV",
            prognose = null,
            meldingTilNAV = MeldingTilNAV(true, "Masse bistand"),
            skjermesForPasient = skjermet,
            behandletTidspunkt = getNowTickMillisLocalDateTime(),
            behandler = Behandler(
                "fornavn",
                null,
                "etternavn",
                "aktorId",
                "01234567891",
                null,
                null,
                Adresse(null, null, null, null, null),
                null,
            ),
            kontaktMedPasient = KontaktMedPasient(
                LocalDate.now(),
                "Begrunnelse",
            ),
            utdypendeOpplysninger = emptyMap(),
            msgId = "msgid",
            pasientAktoerId = "aktorId",
            avsenderSystem = AvsenderSystem("Navn", "verjosn"),
            perioder = perioder,
            signaturDato = getNowTickMillisLocalDateTime(),
        ),
        merknader = null,
        utenlandskSykmelding = null,
    )
}

fun getPeriode(fom: LocalDate, tom: LocalDate, gradert: Gradert? = null): Periode {
    return Periode(
        fom = fom,
        tom = tom,
        aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak("beskrivelse", emptyList()), arbeidsrelatertArsak = null),
        gradert = gradert,
        behandlingsdager = null,
        reisetilskudd = false,
        avventendeInnspillTilArbeidsgiver = null,
    )
}

fun getSykmeldingerDBmodelEgenmeldt(hovediagnosekode: String = "kode", bidiagnoser: List<Diagnose> = emptyList(), avsenderSystem: AvsenderSystem = AvsenderSystem("Nobody", "versjon"), perioder: List<Periode> = emptyList()): SykmeldingDbModel {
    return SykmeldingDbModel(
        id = "123",
        behandlingsutfall = ValidationResult(Status.OK, emptyList()),
        mottattTidspunkt = getNowTickMillisOffsetDateTime(),
        status = StatusDbModel(
            statusEvent = "APEN",
            arbeidsgiver = null,
            statusTimestamp = getNowTickMillisOffsetDateTime(),
        ),
        legekontorOrgNr = "123456789",
        sykmeldingsDokument = Sykmelding(
            id = "123",
            arbeidsgiver = Arbeidsgiver(
                harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                navn = "navn",
                stillingsprosent = null,
                yrkesbetegnelse = null,
            ),
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose("system", hovediagnosekode, "tekst"),
                biDiagnoser = bidiagnoser,
                yrkesskade = false,
                svangerskap = false,
                annenFraversArsak = null,
                yrkesskadeDato = null,
            ),
            andreTiltak = null,
            meldingTilArbeidsgiver = null,
            navnFastlege = null,
            tiltakArbeidsplassen = null,
            syketilfelleStartDato = null,
            tiltakNAV = null,
            prognose = null,
            meldingTilNAV = null,
            skjermesForPasient = false,
            behandletTidspunkt = getNowTickMillisLocalDateTime(),
            behandler = Behandler(
                "fornavn",
                null,
                "etternavn",
                "aktorId",
                "01234567891",
                null,
                null,
                Adresse(null, null, null, null, null),
                null,
            ),
            kontaktMedPasient = KontaktMedPasient(
                LocalDate.now(),
                "Begrunnelse",
            ),
            utdypendeOpplysninger = emptyMap(),
            msgId = "msgid",
            pasientAktoerId = "aktorId",
            avsenderSystem = avsenderSystem,
            perioder = perioder,
            signaturDato = getNowTickMillisLocalDateTime(),
        ),
        merknader = null,
        utenlandskSykmelding = null,
    )
}

fun getPapirsykmeldingDbModel(
    pasientFnr: String = "12345678912",
    mottattTidspunkt: OffsetDateTime = OffsetDateTime.now(),
): PapirsykmeldingDbModel {
    return PapirsykmeldingDbModel(
        pasientFnr = pasientFnr,
        mottattTidspunkt = mottattTidspunkt,
        sykmelding = Sykmelding(
            id = "1",
            msgId = "2",
            pasientAktoerId = "1",
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(Diagnosekoder.ICPC2_CODE, "L87", null),
                biDiagnoser = emptyList(),
                yrkesskade = false,
                svangerskap = false,
                annenFraversArsak = null,
                yrkesskadeDato = null,
            ),
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(
                harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                navn = "Arbeidsgiver",
                yrkesbetegnelse = "Advokat",
                stillingsprosent = 100,
            ),
            perioder = listOf(
                Periode(
                    mottattTidspunkt.toLocalDate(),
                    mottattTidspunkt.toLocalDate().plusDays(8),
                    aktivitetIkkeMulig = null,
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false,
                ),
            ),
            prognose = null,
            utdypendeOpplysninger = emptyMap(),
            tiltakArbeidsplassen = null,
            meldingTilNAV = null,
            andreTiltak = null,
            tiltakNAV = null,
            meldingTilArbeidsgiver = null,
            kontaktMedPasient = KontaktMedPasient(mottattTidspunkt.toLocalDate(), null),
            behandletTidspunkt = mottattTidspunkt.toLocalDateTime(),
            behandler = Behandler(
                fornavn = "Behandler",
                mellomnavn = null,
                etternavn = "Behandlersen",
                aktoerId = "123",
                fnr = "23456789123",
                hpr = null,
                her = null,
                adresse = Adresse(null, null, null, null, null),
                tlf = null,
            ),
            avsenderSystem = AvsenderSystem(navn = "Papirsykmelding", versjon = "1.0"),
            syketilfelleStartDato = mottattTidspunkt.toLocalDate(),
            signaturDato = mottattTidspunkt.toLocalDateTime(),
            navnFastlege = "Behandler Behandlersen",
        ),
    )
}
