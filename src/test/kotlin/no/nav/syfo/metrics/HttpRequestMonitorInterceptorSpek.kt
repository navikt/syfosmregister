package no.nav.syfo.metrics

import java.util.UUID
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class HttpRequestMonitorInterceptorSpek {
    @Test
    internal fun `Test av at bytting av UUID i path fungerer som forventet UUID byttes ut`() {
        val uuid = UUID.randomUUID().toString()
        val pathMedUuid = "/api/v1/sykmelding/$uuid"

        getLabel(pathMedUuid) shouldBeEqualTo "/api/v1/sykmelding/:id"
    }

    @Test
    internal fun `Test av at bytting av UUID i path fungerer som forventet String som ikke er UUID byttes ikke ut`() {
        val pathUtenUuid = "/api/v1/sykmelding/123-testparam"

        getLabel(pathUtenUuid) shouldBeEqualTo pathUtenUuid
    }

    @Test
    internal fun `Test av at bytting av UUID i path fungerer som forventet String som er mottakId byttes ut`() {
        val pathMedMottakId = "/api/v1/sykmelding/1609200914skip31491.1"

        getLabel(pathMedMottakId) shouldBeEqualTo "/api/v1/sykmelding/:mottakId"
    }

    @Test
    internal fun `Test av at bytting av UUID i path fungerer som forventet String som er annen tilfeldig id byttes ut`() {
        val pathMedAnnenId =
            "/api/v2/sykmeldinger/ID:414d51204d504c5343303320202020201fe12e5705285310"

        getLabel(pathMedAnnenId) shouldBeEqualTo "/api/v2/sykmeldinger/:gammelId"
    }
}
