package no.nav.syfo.util

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.util.pipeline.PipelineContext

const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun PipelineContext<Unit, ApplicationCall>.getFnrFromHeader() =
    call.request.headers["fnr"]
