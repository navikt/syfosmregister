package no.nav.syfo

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

private val vaultApplicationPropertiesPath = Paths.get("/var/run/secrets/nais.io/vault/application.properties")

private val config = Properties().apply {
    putAll(Properties().apply {
        load(Environment::class.java.getResourceAsStream("/application.properties"))
    })
    if (Files.exists(vaultApplicationPropertiesPath)) {
        load(Files.newInputStream(vaultApplicationPropertiesPath))
    }
}
fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

data class Environment(
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaSm2013AutomaticPapirmottakTopic: String = getEnvVar("KAFKA_SM2013_PAPIR_MOTTAK_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val kafkaSm2013AutomaticDigitalHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),

    val applicationPort: Int = config.getProperty("application.port").toInt(),
    val applicationThreads: Int = config.getProperty("application.threads").toInt(),
    val srvsyfosmregisterUsername: String = config.getProperty("serviceuser.username"),
    val srvsyfosmregisterPassword: String = config.getProperty("serviceuser.password")

)
