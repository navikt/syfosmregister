package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val kafkaSm2013AutomaticDigitalHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sm2013RegisterTopic: String = getEnvVar("KAFKA_SM2013_REGISTER_TOPIC", "privat-syfo-sm2013-register"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "syfosmregister"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val appIds: List<String> = getEnvVar("ALLOWED_APP_IDS")
            .split(",")
            .map { it.trim() },
    val clientId: String = getEnvVar("CLIENT_ID"),
    val syfoTilgangskontrollUrl: String = getEnvVar("SYFO_TILGANGSKONTROLL_URL", "http://syfo-tilgangskontroll/syfo-tilgangskontroll/api/tilgang/bruker"),
    val sykmeldingStatusBackupTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_BACKUP_TOPIC", "privat-syfo-register-status-backup"),
    val sykmeldingStatusTopic: String = getEnvVar("KAFKA_SYKMELDING_STATUS_TOPIC", "aapen-syfo-sykmeldingstatus-leesah-v1"),
    val sendSykmeldingKafkaTopic: String = "syfo-sendt-sykmelding",
    val bekreftSykmeldingKafkaTopic: String = "syfo-bekreftet-sykmelding"
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val syfomockUsername: String,
    val syfomockPassword: String,
    val oidcWellKnownUri: String,
    val loginserviceClientId: String,
    val internalJwtIssuer: String,
    val internalJwtWellKnownUri: String,
    val internalLoginServiceClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
