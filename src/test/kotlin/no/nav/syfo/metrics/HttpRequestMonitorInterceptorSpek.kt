package no.nav.syfo.metrics

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import java.util.UUID

class HttpRequestMonitorInterceptorSpek : FunSpec({

    context("Test av at bytting av UUID i path fungerer som forventet") {
        test("UUID byttes ut") {
            val uuid = UUID.randomUUID().toString()
            val pathMedUuid = "/api/v1/sykmelding/$uuid"

            getLabel(pathMedUuid) shouldBeEqualTo "/api/v1/sykmelding/:id"
        }

        test("String som ikke er UUID byttes ikke ut") {
            val pathUtenUuid = "/api/v1/sykmelding/123-testparam"

            getLabel(pathUtenUuid) shouldBeEqualTo pathUtenUuid
        }

        test("String som er mottakId byttes ut") {
            val pathMedMottakId = "/api/v1/sykmelding/1609200914skip31491.1"

            getLabel(pathMedMottakId) shouldBeEqualTo "/api/v1/sykmelding/:mottakId"
        }

        test("String som er annen tilfeldig id byttes ut") {
            val pathMedAnnenId = "/api/v2/sykmeldinger/ID:414d51204d504c5343303320202020201fe12e5705285310"

            getLabel(pathMedAnnenId) shouldBeEqualTo "/api/v2/sykmeldinger/:gammelId"
        }

        test("get correct client name") {
            val clientIdString = "sykmeldinger.teamsykmelding.serviceaccount.identity.linkerd.cluster.local"
            val app = getPreauthorizedApp(clientIdString)
            app shouldNotBe null
            app!!.name shouldBeEqualTo "sykmeldinger"
            app.team shouldBeEqualTo "teamsykmelding"
        }
    }
})
