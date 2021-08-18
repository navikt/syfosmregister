package no.nav.syfo.util

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.util.pipeline.PipelineContext

fun PipelineContext<Unit, ApplicationCall>.getFrnFromHeader() =
    call.request.headers["fnr"]
