package no.nav.syfo.metrics

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.getFileAsString
import org.amshove.kluent.shouldBeEqualTo

class AppNamePathTest :
    FunSpec({
        val preAuthApps: List<PreAuthorizedApp> =
            objectMapper.readValue(getFileAsString("src/test/resources/preauthorized-apps.json"))

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
