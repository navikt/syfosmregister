package no.nav.syfo.util

import io.ktor.server.routing.*

const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun RoutingContext.getFnrFromHeader() = call.request.headers["fnr"]
