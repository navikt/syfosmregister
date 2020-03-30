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

            REGEX.replace(pathMedUuid, ":id") shouldEqual "/api/v1/sykmelding/:id"
        }

        it("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmelding/123-testparam"

            REGEX.replace(pathUtenUuid, ":id") shouldEqual pathUtenUuid
        }
    }
})
