package no.nav.syfo.metrics

import io.ktor.http.HttpHeaders.Origin
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineContext
import no.nav.syfo.log

val REGEX = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

val REGEX_MOTTAKID = """[0-9]{10}[a-z]{4}[0-9]{5}.1""".toRegex()

val REGEX_GAMMEL_ID = """ID:[0-9a-z]{48}""".toRegex()

val CLIENT_ID_HEADER = "l5d-client-id"
fun monitorHttpRequests(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val path = context.request.path()
        val label = getLabel(path)
        if (!path.contains("is_alive") && !path.contains("is_ready") && !path.contains("prometheus")) {
            log.info("origin ${context.request.header(Origin)}, referer ${context.request.header("Referer")}")
            val app = getPreauthorizedApp(context.request.header(CLIENT_ID_HEADER))
            if (app != null) {
                APP_ID_PATH_COUNTER.labels(app.team, app.name, label)
            } else {
                log.warn("No client found in headers")
            }
            ORIGIN_COUNTER.labels(context.request.header(Origin) ?: "no-origin").inc()
            REFERER_COUNTER.labels(context.request.header("Referer") ?: "no-referer").inc()
        }
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}

fun getLabel(path: String): String {
    val utenId = REGEX.replace(path, ":id")
    val utenMottakId = REGEX_MOTTAKID.replace(utenId, ":mottakId")
    return REGEX_GAMMEL_ID.replace(utenMottakId, ":gammelId")
}

fun getPreauthorizedApp(headerValue: String?): PreAuthorizedApp? {
    return headerValue?.let {
        val clientId = it.split(".")
        PreAuthorizedApp(clientId[0], clientId[1])
    }
}
