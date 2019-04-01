package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
    val kafkaSm2013AutomaticPapirmottakTopic: String = getEnvVar("KAFKA_SMPAPIR_AUTOMATIC_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val kafkaSm2013AutomaticDigitalHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val smpapirManualHandlingTopic: String = getEnvVar("KAFKA_SMPAPIR_MANUAL_TOPIC", "privat-syfo-smpapir-manuellBehandling"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sm2013RegisterTopic: String = getEnvVar("KAFKA_SM2013_REGISTER_TOPIC", "privat-syfo-sm2013-register"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "syfosmregister"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val cluster: String = getEnvVar("CLUSTER")
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
