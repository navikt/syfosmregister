package no.nav.syfo.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AnnenFraversArsak
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.ArbeidsrelatertArsak
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ErIArbeid
import no.nav.syfo.model.ErIkkeIArbeid
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SporsmalSvar
import no.nav.syfo.model.Status
import no.nav.syfo.model.SvarRestriksjon
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.sykmelding.db.Merknad
import org.flywaydb.core.Flyway
import java.net.ServerSocket
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TestDB : DatabaseInterface {
    companion object {
        private var staticPG: EmbeddedPostgres = EmbeddedPostgres.start()
        init {
            Flyway.configure().run {
                dataSource(staticPG.postgresDatabase).load().migrate()
            }
        }
    }
    private var pg: EmbeddedPostgres = staticPG

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    fun stop() {
        this.connection.dropData()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM behandlingsutfall").executeUpdate()
        connection.prepareStatement("DELETE FROM sykmeldingsdokument").executeUpdate()
        connection.prepareStatement("DELETE FROM sykmeldingsopplysninger").executeUpdate()
        connection.prepareStatement("DELETE FROM sykmeldingstatus").executeUpdate()
        connection.prepareStatement("DELETE FROM arbeidsgiver").executeUpdate()
        connection.prepareStatement("DELETE FROM svar").executeUpdate()
        connection.prepareStatement("DELETE FROM sporsmal").executeUpdate()
        connection.prepareStatement("ALTER SEQUENCE svar_id_seq RESTART WITH 1").executeUpdate()
        connection.prepareStatement("ALTER SEQUENCE sporsmal_id_seq RESTART WITH 1").executeUpdate()
        connection.commit()
    }
}

fun Connection.getMerknaderForId(id: String): List<Merknad>? =
    this.prepareStatement(
        """
                    SELECT merknader 
                    FROM sykmeldingsopplysninger
                    where id = ?
            """
    ).use {
        it.setString(1, id)
        it.executeQuery().toList { tilMerknadliste() }.firstOrNull()
    }

fun Connection.getSykmeldingsopplysninger(id: String): Sykmeldingsopplysninger? {
    this.prepareStatement(
        """
            SELECT id,
                pasient_fnr,
                pasient_aktoer_id,
                lege_fnr,
                lege_hpr,
                lege_helsepersonellkategori,
                lege_aktoer_id,
                mottak_id,
                legekontor_org_nr,
                legekontor_her_id,
                legekontor_resh_id,
                epj_system_navn,
                epj_system_versjon,
                mottatt_tidspunkt,
                tss_id,
                merknader,
                partnerreferanse  
            FROM SYKMELDINGSOPPLYSNINGER 
            WHERE id = ?;
        """
    ).use {
        it.setString(1, id)
        return it.executeQuery().toList { toSykmeldingsopplysninger() }.firstOrNull()
    }
}

private fun ResultSet.toSykmeldingsopplysninger(): Sykmeldingsopplysninger {
    return Sykmeldingsopplysninger(
        id = getString("id"),
        pasientFnr = getString("pasient_fnr"),
        pasientAktoerId = getString("pasient_aktoer_id"),
        legeFnr = getString("lege_fnr"),
        legeHpr = getString("lege_hpr"),
        legeHelsepersonellkategori = getString("lege_helsepersonellkategori"),
        legeAktoerId = getString("lege_aktoer_id"),
        mottakId = getString("mottak_id"),
        legekontorOrgNr = getString("legekontor_org_nr"),
        legekontorHerId = getString("legekontor_her_id"),
        legekontorReshId = getString("legekontor_resh_id"),
        epjSystemNavn = getString("epj_system_navn"),
        epjSystemVersjon = getString("epj_system_versjon"),
        mottattTidspunkt = getTimestamp("mottatt_tidspunkt").toLocalDateTime(),
        tssid = getString("tss_id"),
        merknader = getString("merknader")?.let { objectMapper.readValue<List<no.nav.syfo.model.Merknad>>(it) },
        partnerreferanse = getString("partnerreferanse")
    )
}

private fun ResultSet.tilMerknadliste(): List<Merknad>? {
    return getString("merknader")?.let { objectMapper.readValue<List<Merknad>>(it) }
}

fun getSykmeldingOpplysninger(fnr: String = "pasientFnr", id: String = "123"): Sykmeldingsopplysninger {
    return testSykmeldingsopplysninger.copy(pasientFnr = fnr, id = id)
}

val testSykmeldingsopplysninger = Sykmeldingsopplysninger(
    id = "uuid",
    pasientFnr = "pasientFnr",
    pasientAktoerId = "pasientAktorId",
    legeFnr = "legeFnr",
    legeHpr = "123774",
    legeHelsepersonellkategori = "LE",
    legeAktoerId = "legeAktorId",
    mottakId = "eid-1",
    legekontorOrgNr = "lege-orgnummer",
    legekontorHerId = "legekontorHerId",
    legekontorReshId = "legekontorReshId",
    epjSystemNavn = "epjSystemNavn",
    epjSystemVersjon = "epjSystemVersjon",
    mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).toLocalDateTime(),
    tssid = "13455",
    merknader = emptyList(),
    partnerreferanse = null
)

val testSykmeldingsdokument = Sykmeldingsdokument(
    id = "uuid",
    sykmelding = Sykmelding(
        andreTiltak = "andreTiltak",
        arbeidsgiver = Arbeidsgiver(
            harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
            navn = "Arbeidsgiver AS",
            yrkesbetegnelse = "aktiv",
            stillingsprosent = 100
        ),
        avsenderSystem = AvsenderSystem(
            navn = "avsenderSystem",
            versjon = "versjon-1.0"
        ),
        behandler = Behandler(
            fornavn = "Fornavn",
            mellomnavn = "Mellomnavn",
            aktoerId = "legeAktorId",
            etternavn = "Etternavn",
            adresse = Adresse(
                gate = null,
                postboks = null,
                postnummer = null,
                kommune = null,
                land = null
            ),
            fnr = "legeFnr",
            hpr = "hpr",
            her = "her",
            tlf = "tlf"
        ),
        behandletTidspunkt = LocalDateTime.now(),
        id = "id",
        kontaktMedPasient = KontaktMedPasient(
            kontaktDato = null,
            begrunnelseIkkeKontakt = null
        ),
        medisinskVurdering = MedisinskVurdering(
            hovedDiagnose = Diagnose("2.16.578.1.12.4.1.1.7170", "Z01", "Brukket fot"),
            biDiagnoser = emptyList(),
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null,
            annenFraversArsak = AnnenFraversArsak(null, emptyList())
        ),
        meldingTilArbeidsgiver = "",
        meldingTilNAV = MeldingTilNAV(false, null),
        msgId = "msgId",
        pasientAktoerId = "pasientAktoerId",
        perioder = listOf(
            Periode(
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                aktivitetIkkeMulig = AktivitetIkkeMulig(
                    medisinskArsak = MedisinskArsak(null, emptyList()),
                    arbeidsrelatertArsak = ArbeidsrelatertArsak(null, emptyList())
                ),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                gradert = Gradert(false, 0),
                reisetilskudd = false
            )
        ),
        prognose = Prognose(
            true,
            null,
            ErIArbeid(false, false, null, null),
            ErIkkeIArbeid(false, null, null)
        ),
        signaturDato = LocalDateTime.now(),
        skjermesForPasient = false,
        syketilfelleStartDato = LocalDate.now(),
        tiltakArbeidsplassen = "tiltakArbeidsplassen",
        tiltakNAV = "tiltakNAV",
        utdypendeOpplysninger = getUtdypendeOpplysninger(),
        navnFastlege = "Per Hansen"
    )
)

fun getUtdypendeOpplysninger(): Map<String, Map<String, SporsmalSvar>> {
    val map = HashMap<String, HashMap<String, SporsmalSvar>>()
    val map62 = HashMap<String, SporsmalSvar>()
    map62.put("6.2.1", SporsmalSvar(sporsmal = "Beskriv kort sykehistorie, symptomer og funn i dagens situasjon.", svar = "Veldig syk med masse feber.", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)))
    map62.put("6.2.2", SporsmalSvar(sporsmal = "sporsaml", svar = "svar", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)))
    map62.put("6.2.3", SporsmalSvar(sporsmal = "sporsmal", svar = "svar", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)))
    map62.put("6.2.4", SporsmalSvar(sporsmal = "sporsmal", svar = "svar", restriksjoner = listOf(SvarRestriksjon.SKJERMET_FOR_ARBEIDSGIVER)))
    map.put("6.2", map62)
    return map
}

val testBehandlingsutfall = Behandlingsutfall(
    id = "uuid",
    behandlingsutfall = ValidationResult(Status.OK, emptyList())
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
