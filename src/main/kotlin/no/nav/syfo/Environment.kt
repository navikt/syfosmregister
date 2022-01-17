package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmregister"),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "syfosmregister"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfosmregisterDBURL: String = getEnvVar("SYFOSMREGISTER_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val syfoTilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val sykmeldingStatusAivenTopic: String = "teamsykmelding.sykmeldingstatus-leesah",
    val sendSykmeldingKafkaTopic: String = "teamsykmelding.syfo-sendt-sykmelding",
    val bekreftSykmeldingKafkaTopic: String = "teamsykmelding.syfo-bekreftet-sykmelding",
    val mottattSykmeldingKafkaTopic: String = "teamsykmelding.syfo-mottatt-sykmelding",
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val jwkKeysUrlV2: String = getEnvVar("AZURE_OPENID_CONFIG_JWKS_URI"),
    val jwtIssuerV2: String = getEnvVar("AZURE_OPENID_CONFIG_ISSUER"),
    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val azureTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val pdlAktorTopic: String = "aapen-person-pdl-aktor-v1",
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val avvistSykmeldingTopic: String = "teamsykmelding.avvist-sykmelding",
    val manuellSykmeldingTopic: String = "teamsykmelding.manuell-behandling-sykmelding",
    val behandlingsUtfallTopic: String = "teamsykmelding.sykmelding-behandlingsutfall"
) : KafkaConfig

data class VaultServiceUser(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

data class VaultSecrets(
    val syfomockUsername: String,
    val syfomockPassword: String,
    val internalJwtIssuer: String,
    val internalLoginServiceClientId: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
