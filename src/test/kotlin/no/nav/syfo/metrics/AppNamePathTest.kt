package no.nav.syfo.metrics

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.objectMapper
import no.nav.syfo.testutil.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

internal class AppNamePathTest {
    val preAuthApps: List<PreAuthorizedApp> =
        objectMapper.readValue(getFileAsString("src/test/resources/preauthorized-apps.json"))

    @Test
    internal fun `should get correct app name`() {
        val appName = preAuthApps.firstOrNull { it.clientId == "1" }
        appName?.appName shouldBeEqualTo "app1"
        appName?.team shouldBeEqualTo "team1"
    }

    @Test
    internal fun `should get null if not in list`() {

        val appName = preAuthApps.firstOrNull { it.clientId == "4" }
        appName shouldBeEqualTo null
    }
}
