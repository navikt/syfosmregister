package no.nav.syfo.metrics

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

class HttpRequestMonitorInterceptorSpek : Spek({

    describe("Test av at bytting av UUID i path fungerer som forventet") {
        it("UUID byttes ut") {
            val uuid = UUID.randomUUID().toString()
            val pathMedUuid = "/api/v1/sykmelding/$uuid"

            getLabel(pathMedUuid) shouldBeEqualTo "/api/v1/sykmelding/:id"
        }

        it("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmelding/123-testparam"

            getLabel(pathUtenUuid) shouldBeEqualTo pathUtenUuid
        }

        it("String som er mottakId byttes ut") {
            val pathMedMottakId = "/api/v1/sykmelding/1609200914skip31491.1"

            getLabel(pathMedMottakId) shouldBeEqualTo "/api/v1/sykmelding/:mottakId"
        }

        it("String som er annen tilfeldig id byttes ut") {
            val pathMedAnnenId = "/api/v2/sykmeldinger/ID:414d51204d504c5343303320202020201fe12e5705285310"

            getLabel(pathMedAnnenId) shouldBeEqualTo "/api/v2/sykmeldinger/:gammelId"
        }
    }
})
