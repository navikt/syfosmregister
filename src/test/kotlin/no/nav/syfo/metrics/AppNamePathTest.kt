package no.nav.syfo.metrics

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.jsonMapper
import no.nav.syfo.testutil.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import tools.jackson.module.kotlin.readValue

class AppNamePathTest :
    FunSpec({
        val preAuthApps: List<PreAuthorizedApp> =
            jsonMapper.readValue(getFileAsString("src/test/resources/preauthorized-apps.json"))

        test("should get correct app name") {
            val appName = preAuthApps.firstOrNull { it.clientId == "1" }
            appName?.appName shouldBeEqualTo "app1"
            appName?.team shouldBeEqualTo "team1"
        }

        test("should get null if not in list") {
            val appName = preAuthApps.firstOrNull { it.clientId == "4" }
            appName shouldBeEqualTo null
        }
    })
