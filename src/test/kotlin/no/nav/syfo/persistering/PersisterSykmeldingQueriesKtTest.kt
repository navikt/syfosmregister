package no.nav.syfo.persistering

import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getSykmeldingsopplysninger
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PersisterSykmeldingQueriesKtTest {
    val db = TestDB.database

    @AfterEach
    fun afterEach() {
        db.connection.dropData()
    }

    @Test
    internal fun `Test at Sykmeldingsopplysninger persisteres og hentes ut riktig`() {
        val sykmeldingsId = UUID.randomUUID().toString()
        val sykmeldingsOpplysninger =
            testSykmeldingsopplysninger.copy(
                id = sykmeldingsId,
                legeHpr = "hpr",
                legeHelsepersonellkategori = "LE",
            )

        runBlocking {
            db.lagreMottattSykmelding(
                sykmeldingsOpplysninger,
                testSykmeldingsdokument.copy(id = sykmeldingsId),
            )
            val fromDb = db.connection.getSykmeldingsopplysninger(id = sykmeldingsId)

            sykmeldingsOpplysninger shouldBeEqualTo fromDb
        }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun afterSpec(): Unit {
            TestDB.stop()
        }
    }
}
