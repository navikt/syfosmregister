package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM sykmeldingsopplysninger").executeUpdate()
        connection.commit()
    }
}

val testSykmeldingsopplysninger = Sykmeldingsopplysninger(
    id = "uuid",
    pasientFnr = "pasientFnr",
    pasientAktoerId = "pasientAktorId",
    legeFnr = "legeFnr",
    legeAktoerId = "legeAktorId",
    mottakId = "eid-1",
    legekontorOrgNr = "lege-orgnummer",
    legekontorHerId = "legekontorHerId",
    legekontorReshId = "legekontorReshId",
    epjSystemNavn = "epjSystemNavn",
    epjSystemVersjon = "epjSystemVersjon",
    mottattTidspunkt = LocalDateTime.now(),
    tssid = "13455"
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
            hovedDiagnose = Diagnose("2.16.578.1.12.4.1.1.7170", "Z01"),
            biDiagnoser = emptyList(),
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null,
            annenFraversArsak = null
        ),
        meldingTilArbeidsgiver = "",
        meldingTilNAV = null,
        msgId = "msgId",
        pasientAktoerId = "pasientAktoerId",
        perioder = listOf(
            Periode(
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                aktivitetIkkeMulig = AktivitetIkkeMulig(
                    medisinskArsak = null,
                    arbeidsrelatertArsak = null
                ),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                gradert = null,
                reisetilskudd = false
            )
        ),
        prognose = null,
        signaturDato = LocalDateTime.now(),
        skjermesForPasient = false,
        syketilfelleStartDato = LocalDate.now(),
        tiltakArbeidsplassen = "tiltakArbeidsplassen",
        tiltakNAV = "tiltakNAV",
        utdypendeOpplysninger = emptyMap()
    )
)

val testBehandlingsutfall = Behandlingsutfall(
    id = "uuid",
    behandlingsutfall = ValidationResult(Status.OK, emptyList())
)
