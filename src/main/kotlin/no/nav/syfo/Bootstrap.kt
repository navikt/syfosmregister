package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.prometheus.client.hotspot.DefaultExports
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnownTokenX
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.db.Database
import no.nav.syfo.identendring.IdentendringService
import no.nav.syfo.identendring.PdlAktorConsumer
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.internal.tilgang.TilgangskontrollService
import no.nav.syfo.sykmelding.kafka.KafkaFactory
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getKafkaConsumerAivenPdlAktor
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getKafkaStatusConsumerAiven
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getMottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.KafkaFactory.Companion.getSykmeldingStatusKafkaProducer
import no.nav.syfo.sykmelding.kafka.service.MottattSykmeldingStatusService
import no.nav.syfo.sykmelding.kafka.service.SykmeldingStatusConsumerService
import no.nav.syfo.sykmelding.service.MottattSykmeldingConsumerService
import no.nav.syfo.sykmelding.service.MottattSykmeldingService
import no.nav.syfo.sykmelding.service.SykmeldingerService
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import no.nav.syfo.util.handleResponseException
import no.nav.syfo.util.setupJacksonSerialization
import no.nav.syfo.util.setupRetry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

val log: Logger = LoggerFactory.getLogger("nav.syfo.syfosmregister")
val securelog: Logger = LoggerFactory.getLogger("securelog")

@DelicateCoroutinesApi
@ExperimentalTime
fun main() {
    val environment = Environment()

    val jwkProviderAadV2 =
        JwkProviderBuilder(URI.create(environment.jwkKeysUrlV2).toURL())
            .cached(10, Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val wellKnownTokenX = getWellKnownTokenX(environment.tokenXWellKnownUrl)
    val jwkProviderTokenX =
        JwkProviderBuilder(URI.create(wellKnownTokenX.jwks_uri).toURL())
            .cached(10, Duration.ofHours(24))
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val database = Database(environment)

    val applicationState = ApplicationState()

    DefaultExports.initialize()

    val sykmeldingStatusService = SykmeldingStatusService(database)

    val sykmeldingStatusKafkaConsumerAiven = getKafkaStatusConsumerAiven(environment)

    val receivedSykmeldingKafkaConsumerAiven =
        KafkaConsumer<String, String>(
            KafkaUtils.getAivenKafkaConfig("received-sykmelding-consumer")
                .also {
                    it.let {
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    }
                }
                .toConsumerConfig(
                    "${environment.applicationName}-gcp-consumer",
                    valueDeserializer = StringDeserializer::class
                )
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" },
        )

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        setupJacksonSerialization()
        handleResponseException()
        setupRetry()
        expectSuccess = true
    }

    val httpClient = HttpClient(Apache, config)
    val azureAdV2Client =
        AzureAdV2Client(
            environment.clientIdV2,
            environment.clientSecretV2,
            environment.azureTokenEndpoint,
            httpClient
        )
    val tilgangskontrollService =
        TilgangskontrollService(
            azureAdV2Client,
            httpClient,
            environment.istilgangskontrollUrl,
            environment.istilgangskontrollScope
        )

    val pdlClient =
        PdlClient(
            httpClient,
            environment.pdlGraphqlPath,
            PdlClient::class
                .java
                .getResource("/graphql/getPerson.graphql")
                .readText()
                .replace(Regex("[\n\t]"), ""),
        )
    val pdlService = PdlPersonService(pdlClient, azureAdV2Client, environment.pdlScope)

    val sykmeldingerService = SykmeldingerService(database)
    val sendtSykmeldingKafkaProducer = KafkaFactory.getSendtSykmeldingKafkaProducer(environment)
    val bekreftSykmeldingKafkaProducer =
        KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment)
    val tombstoneProducer = KafkaFactory.getTombstoneProducer(environment)
    val mottattSykmeldingStatusService =
        MottattSykmeldingStatusService(
            sykmeldingStatusService,
            sendtSykmeldingKafkaProducer,
            bekreftSykmeldingKafkaProducer,
            tombstoneProducer,
            database
        )

    val leaderElection = LeaderElection(httpClient, environment.electorPath)
    val identendringService =
        IdentendringService(database, sendtSykmeldingKafkaProducer, pdlService)
    val pdlAktorConsumer =
        PdlAktorConsumer(
            kafkaConsumerAiven = getKafkaConsumerAivenPdlAktor(environment),
            applicationState = applicationState,
            aivenTopic = environment.pdlAktorV2Topic,
            leaderElection = leaderElection,
            identendringService = identendringService,
        )

    val mottattSykmeldingService =
        MottattSykmeldingService(
            database = database,
            env = environment,
            sykmeldingStatusKafkaProducer = getSykmeldingStatusKafkaProducer(environment),
            mottattSykmeldingKafkaProducer = getMottattSykmeldingKafkaProducer(environment),
            mottattSykmeldingStatusService = mottattSykmeldingStatusService,
            sykmeldingStatusService = sykmeldingStatusService,
        )
    val sykmeldingStatusConsumerService =
        SykmeldingStatusConsumerService(
            sykmeldingStatusKafkaConsumer = sykmeldingStatusKafkaConsumerAiven,
            applicationState = applicationState,
            mottattSykmeldingStatusService = mottattSykmeldingStatusService,
        )
    val mottattSykmeldingConsumerService =
        MottattSykmeldingConsumerService(
            applicationState = applicationState,
            kafkaAivenConsumer = receivedSykmeldingKafkaConsumerAiven,
            mottattSykmeldingService = mottattSykmeldingService,
            env = environment,
        )

    val applicationEngine =
        createApplicationEngine(
            env = environment,
            applicationState = applicationState,
            database = database,
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = wellKnownTokenX.issuer,
            jwkProviderAadV2 = jwkProviderAadV2,
            sykmeldingerService = sykmeldingerService,
            tilgangskontrollService = tilgangskontrollService,
        )

    pdlAktorConsumer.startConsumer()

    launchListeners(
        applicationState = applicationState,
        sykmeldingStatusConsumerService = sykmeldingStatusConsumerService,
        mottattSykmeldingConsumerService = mottattSykmeldingConsumerService,
    )

    ApplicationServer(applicationEngine, applicationState).start()
}

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    GlobalScope.launch(Dispatchers.IO) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta),
                e.cause
            )
        } catch (ex: Exception) {
            log.error("Noe gikk galt", ex.cause)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    applicationState: ApplicationState,
    sykmeldingStatusConsumerService: SykmeldingStatusConsumerService,
    mottattSykmeldingConsumerService: MottattSykmeldingConsumerService,
) {
    createListener(applicationState) { mottattSykmeldingConsumerService.start() }
    createListener(applicationState) { sykmeldingStatusConsumerService.start() }
}
