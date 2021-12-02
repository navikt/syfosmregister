package no.nav.syfo.metrics

import java.util.UUID
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class HttpRequestMonitorInterceptorSpek : Spek({

    describe("Test av at bytting av UUID i path fungerer som forventet") {
        it("UUID byttes ut") {
            val uuid = UUID.randomUUID().toString()
            val pathMedUuid = "/api/v1/sykmelding/$uuid"

            getLabel(pathMedUuid) shouldEqual "/api/v1/sykmelding/:id"
        }

        it("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmelding/123-testparam"

            getLabel(pathUtenUuid) shouldEqual pathUtenUuid
        }

        it("String som er mottakId byttes ut") {
            val pathMedMottakId = "/api/v1/sykmelding/1609200914skip31491.1"

            getLabel(pathMedMottakId) shouldEqual "/api/v1/sykmelding/:mottakId"
        }

        it("String som er annen tilfeldig id byttes ut") {
            val pathMedAnnenId = "/api/v2/sykmeldinger/ID:414d51204d504c5343303320202020201fe12e5705285310"

            getLabel(pathMedAnnenId) shouldEqual "/api/v2/sykmeldinger/:gammelId"
        }
    }
})
