package no.nav.syfo.metrics

data class PreAuthorizedApp(
    val name: String,
    val clientId: String
) {
    val appName = name.split(":").last()
    val team = name.split(":")[1]
}
