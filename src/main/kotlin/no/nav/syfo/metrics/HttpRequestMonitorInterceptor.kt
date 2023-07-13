package no.nav.syfo.metrics

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineContext

val REGEX =
    """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

val REGEX_MOTTAKID = """[0-9]{10}[a-z]{4}[0-9]{5}.1""".toRegex()

val REGEX_GAMMEL_ID = """ID:[0-9a-z]{48}""".toRegex()

fun monitorHttpRequests(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val path = context.request.path()
        val label = getLabel(path)
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
