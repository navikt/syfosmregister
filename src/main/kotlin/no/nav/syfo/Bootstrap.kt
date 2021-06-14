package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getBekreftetSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getKafkaStatusConsumer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getMottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getTombstoneProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmelding.service.BehandlingsutfallService
import no.nav.syfo.sykmelding.service.MottattSykmeldingService
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")

@KtorExperimentalAPI
fun main() {
    val environment = Environment()
    val vaultSecrets =
            objectMapper.readValue<VaultSecrets>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val wellKnown = getWellKnown(environment.loginserviceIdportenDiscoveryUrl)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val jwkProviderInternal = JwkProviderBuilder(URL(vaultSecrets.internalJwtWellKnownUri))
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

    val kafkaBaseConfig = loadBaseConfig(environment, vaultSecrets)
        .also {
            it["auto.offset.reset"] = "none"
        }
        .envOverrides()

    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )

    val sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(kafkaBaseConfig, environment)
    val sykmeldingStatusService = SykmeldingStatusService(database)
    val sendtSykmeldingKafkaProducer = getSendtSykmeldingKafkaProducer(kafkaBaseConfig, environment)
    val bekreftSykmeldingKafkaProducer = getBekreftetSykmeldingKafkaProducer(kafkaBaseConfig, environment)
    val sykmeldingStatusKafkaConsumer = getKafkaStatusConsumer(kafkaBaseConfig, environment)
    val tombstoneProducer = getTombstoneProducer(kafkaBaseConfig, environment)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, tombstoneProducer)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusKafkaConsumer, applicationState, mottattSykmeldingStatusService)
    val mottattSykmeldingKafkaProducer = getMottattSykmeldingKafkaProducer(kafkaBaseConfig, environment)

    val receivedSykmeldingKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    val mottattSykmeldingService = MottattSykmeldingService(applicationState = applicationState,
            env = environment,
            kafkaconsumer = receivedSykmeldingKafkaConsumer,
            database = database,
            sykmeldingStatusKafkaProducer = sykmeldingStatusKafkaProducer,
            mottattSykmeldingKafkaProducer = mottattSykmeldingKafkaProducer)

    val behandlingsutfallKafkaConsumer = KafkaConsumer<String, String>(consumerProperties)
    val behandligsutfallService = BehandlingsutfallService(
            applicationState = applicationState,
            kafkaconsumer = behandlingsutfallKafkaConsumer,
            env = environment,
            database = database
    )

    val applicationEngine = createApplicationEngine(
        env = environment,
        applicationState = applicationState,
        database = database,
        vaultSecrets = vaultSecrets,
        jwkProvider = jwkProvider,
        issuer = wellKnown.issuer,
        cluster = environment.cluster,
        jwkProviderInternal = jwkProviderInternal,
        sykmeldingStatusService = sykmeldingStatusService,
        issuerServiceuser = environment.jwtIssuer,
        clientId = environment.clientId,
        appIds = environment.appIds,
        jwkProviderAadV2 = jwkProviderAadV2
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    launchListeners(
            applicationState = applicationState,
            sykmeldingStatusConsumerService = sykmeldingStatusConsumerService,
            mottattSykmeldingService = mottattSykmeldingService,
            behandligsutfallService = behandligsutfallService
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
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

@KtorExperimentalAPI
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
