package no.nav.syfo.persistering

import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.getSykmeldingsopplysninger
import no.nav.syfo.testutil.testSykmeldingsdokument
import no.nav.syfo.testutil.testSykmeldingsopplysninger
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class PersisterSykmeldingQueriesKtTest : Spek({
    val db = TestDB()

    afterEachTest {
        db.connection.dropData()
    }

    afterGroup {
        db.stop()
    }

    describe("Test at Sykmeldingsopplysninger persisteres og hentes ut riktig") {

        val sykmeldingsId = UUID.randomUUID().toString()
        val sykmeldingsOpplysninger = testSykmeldingsopplysninger
            .copy(
                id = sykmeldingsId,
                legeHpr = "hpr",
                legeHelsepersonellkategori = "LE"
            )

        db.lagreMottattSykmelding(sykmeldingsOpplysninger, testSykmeldingsdokument.copy(id = sykmeldingsId))
        val fromDb = db.connection.getSykmeldingsopplysninger(id = sykmeldingsId)

        sykmeldingsOpplysninger shouldBeEqualTo fromDb
    }
})
