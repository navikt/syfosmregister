package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = 8080,
    val applicationThreads: Int = 1,
    val kafkaSm2013AutomaticPapirmottakTopic: String,
    val kafkaSm2013AutomaticDigitalHandlingTopic: String,
    val kafkaBootstrapServers: String,
    val syfosmregisterDBURL: String,
    val mountPathVault: String,
    val cluster: String,
    val databaseName: String,
    val applicationName: String,
    val sm2013ManualHandlingTopic: String
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String
)
