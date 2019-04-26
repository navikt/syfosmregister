package no.nav.syfo

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.syfo.model.PersistedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.flywaydb.core.Flyway
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime

object DatabaseSpek : Spek({

    val postgres = EmbeddedPostgres.start()
    Flyway.configure().run {
        dataSource(postgres.postgresDatabase).load().migrate()
    }
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = postgres.getJdbcUrl("postgres", "postgres")
        username = "postgres"
        password = "postgres"
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    })

    val sykmelding = generateSykmelding(
        perioder = listOf(
            generatePeriode(
                fom = LocalDate.now(),
                tom = LocalDate.now().plusMonths(3).plusDays(1)
            )
        )
    )

    val behandlingsUtfall = ValidationResult(
        Status.INVALID,
        listOf(
            RuleInfo(
                "OVER_70_AR",
                "Gamlis",
                "Du er for gammal!"
            )
        )
    )

    val persistedSykmelding = PersistedSykmelding(
        id = "id",
        pasientAktoerId = "akterid",
        pasientFnr = "fnr",
        legeFnr = "legeFnr",
        legeAktoerId = "legeAktorid",
        mottakId = "mottakid",
        legekontorOrgNr = "orgnr",
        legekontorHerId = "her",
        legekontorReshId = "resh",
        epjSystemNavn = "Best EPJ",
        epjSystemVersjon = "0.0.0.0.0.0.1-SNAPSHOT",
        mottattTidspunkt = LocalDateTime.now(),
        sykmelding = sykmelding,
        behandlingsUtfall = behandlingsUtfall

    )

    describe("Register database") {
        it("should be able to insert persisted sykemelding") {
            val connection = postgres.getDatabase("postgres", "postgres").connection
            connection.autoCommit = false

            // TODO insertSykmelding(persistedSykmelding, connection)
        }

        it("should be able to get persisted sykemelding for user") {
            val connection = postgres.getDatabase("postgres", "postgres").connection
            connection.autoCommit = false

            // TODO findWithFnr("fnr", connection) shouldEqual listOf(persistedSykmelding)
        }

        it("should be able to check if sykmelding is stored") {
            val connection = postgres.getDatabase("postgres", "postgres").connection
            connection.autoCommit = false

            // TODO assertTrue(isSykemeldingStored("id", connection))
        }

        it("should be able to get specific sykmelding for person") {
            val connection = postgres.getDatabase("postgres", "postgres").connection
            connection.autoCommit = false

            // TODO findWithId("id", "fnr", connection) shouldEqual persistedSykmelding
        }
    }
})
