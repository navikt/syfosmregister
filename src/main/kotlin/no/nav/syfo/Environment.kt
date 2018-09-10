package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsyfosmregisterUsername: String = getEnvVar("SRVSYFOSMREGISTER_USERNAME"),
    val srvsyfosmregisterPassword: String = getEnvVar("SRVSYFOSMREGISTER_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaSm2013AutomaticPapirmottakTopic: String = getEnvVar("KAFKA_SM2013_PAPIR_MOTTAK_TOPIC", "privat-syfo-smpapir-automatiskBehandling"),
    val kafkaSm2013AutomaticDigitalHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling")

)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
