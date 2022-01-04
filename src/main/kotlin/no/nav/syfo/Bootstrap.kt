package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.application.proxyConfig
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.identendring.IdentendringService
import no.nav.syfo.identendring.PdlAktorConsumer
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getBekreftetSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getKafkaConsumerPdlAktor
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getKafkaStatusConsumer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getMottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getTombstoneProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmelding.service.BehandlingsutfallService
import no.nav.syfo.sykmelding.service.MottattSykmeldingService
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.util.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")

@ExperimentalTime
fun main() {
    val environment = Environment()
    val vaultServiceUser = VaultServiceUser()
    val vaultSecrets =
        objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val wellKnown = getWellKnown(environment.loginserviceIdportenDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderAadV2 = JwkProviderBuilder(URL(environment.jwkKeysUrlV2))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(environment, vaultCredentialService)

    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(environment, vaultServiceUser)
        .also {
            it["auto.offset.reset"] = "none"
        }
        .envOverrides()

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )

    val kafkaBaseConfigAiven = KafkaUtils.getAivenKafkaConfig()

    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(kafkaBaseConfig, environment)
    val sykmeldingStatusService = SykmeldingStatusService(database)
    val sendtSykmeldingKafkaProducer = getSendtSykmeldingKafkaProducer(kafkaBaseConfigAiven, environment)
    val bekreftSykmeldingKafkaProducer = getBekreftetSykmeldingKafkaProducer(kafkaBaseConfigAiven, environment)
    val sykmeldingStatusKafkaConsumer = getKafkaStatusConsumer(kafkaBaseConfig, environment)
    val tombstoneProducer = getTombstoneProducer(kafkaBaseConfig, environment)
    val mottattSykmeldingKafkaProducer = getMottattSykmeldingKafkaProducer(kafkaBaseConfigAiven, environment)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, mottattSykmeldingKafkaProducer, tombstoneProducer, database)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusKafkaConsumer, applicationState, mottattSykmeldingStatusService)

    val receivedSykmeldingKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    val mottattSykmeldingService = MottattSykmeldingService(
        applicationState = applicationState,
        env = environment,
        kafkaconsumer = receivedSykmeldingKafkaConsumer,
        database = database,
        sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
        mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer,
        mottattSykmeldingStatusService = mottattSykmeldingStatusService
    )

    val behandlingsutfallKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    val behandligsutfallService = BehandlingsutfallService(
        applicationState = applicationState,
        kafkaconsumer = behandlingsutfallKafkaConsumer,
        env = environment,
        database = database
    )

    val identendringService = IdentendringService(database, sendtSykmeldingKafkaProducer)
    val pdlAktorConsumer = PdlAktorConsumer(getKafkaConsumerPdlAktor(vaultServiceUser, environment), applicationState, environment.pdlAktorTopic, identendringService)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)

    val sykmeldingerService = SykmeldingerService(database)

    val httpProxyClient = HttpClient(Apache, proxyConfig)
    val azureAdV2Client = AzureAdV2Client(environment.clientIdV2, environment.clientSecretV2, environment.azureTokenEndpoint, httpProxyClient)
    val tilgangskontrollService = TilgangskontrollService(azureAdV2Client, httpClient, environment.syfoTilgangskontrollUrl, environment.syfotilgangskontrollClientId)

    val applicationEngine = createApplicationEngine(
        env = environment,
        applicationState = applicationState,
        database = database,
        vaultSecrets = vaultSecrets,
        jwkProvider = jwkProvider,
        issuer = wellKnown.issuer,
        cluster = environment.cluster,
        sykmeldingStatusService = sykmeldingStatusService,
        jwkProviderAadV2 = jwkProviderAadV2,
        sykmeldingerService = sykmeldingerService,
        tilgangskontrollService = tilgangskontrollService
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    pdlAktorConsumer.startConsumer()
    launchListeners(
        applicationState = applicationState,
        sykmeldingStatusConsumerService = sykmeldingStatusConsumerService,
        mottattSykmeldingService = mottattSykmeldingService,
        behandligsutfallService = behandligsutfallService
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } catch (ex: Exception) {
            log.error("Noe gikk galt", ex.cause)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

fun launchListeners(
    applicationState: ApplicationState,
    sykmeldingStatusConsumerService: SykmeldingStatusConsumerService,
    mottattSykmeldingService: MottattSykmeldingService,
    behandligsutfallService: BehandlingsutfallService
) {
    createListener(applicationState) {
        mottattSykmeldingService.start()
    }
    createListener(applicationState) {
        behandligsutfallService.start()
    }
    createListener(applicationState) {
        sykmeldingStatusConsumerService.start()
    }
}
