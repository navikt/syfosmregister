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
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.Behandlingsutfall
import no.nav.syfo.persistering.Sykmeldingsdokument
import no.nav.syfo.persistering.Sykmeldingsopplysninger
import no.nav.syfo.persistering.erBehandlingsutfallLagret
import no.nav.syfo.persistering.erSykmeldingsopplysningerLagret
import no.nav.syfo.persistering.opprettBehandlingsutfall
import no.nav.syfo.persistering.opprettSykmeldingsdokument
import no.nav.syfo.persistering.opprettSykmeldingsopplysninger
import no.nav.syfo.rerunkafka.kafka.RerunKafkaProducer
import no.nav.syfo.rerunkafka.service.RerunKafkaService
import no.nav.syfo.sykmeldingstatus.StatusEvent
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusEvent
import no.nav.syfo.sykmeldingstatus.registerStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
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
    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val jwkProviderForRerun = JwkProviderBuilder(URL(environment.jwkKeysUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val jwkProviderStsOidc = JwkProviderBuilder(URL(vaultSecrets.stsOidcWellKnownUri))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(environment, vaultCredentialService)

    val applicationState = ApplicationState()

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(environment, vaultSecrets).envOverrides()
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
            "${environment.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )
    val producerConfig = kafkaBaseConfig.toProducerConfig(
            "${environment.applicationName}-producer", valueSerializer = StringSerializer::class
    )
    val kafkaProducer = KafkaProducer<String, String>(producerConfig)
    val rerunKafkaProducer = RerunKafkaProducer(kafkaProducer, environment)
    val rerunKafkaService = RerunKafkaService(database, rerunKafkaProducer)
    val applicationEngine = createApplicationEngine(
            environment,
            applicationState,
            database,
            vaultSecrets,
            jwkProvider,
            wellKnown.issuer,
            environment.cluster,
            rerunKafkaService,
            jwkProviderForRerun,
            jwkProviderStsOidc
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true
    launchListeners(
            environment,
            applicationState,
            database,
            consumerProperties
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
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    database: Database,
    consumerProperties: Properties
) {
    val kafkaconsumerRecievedSykmelding = KafkaConsumer<String, String>(consumerProperties)
    kafkaconsumerRecievedSykmelding.subscribe(
            listOf(
                    env.sm2013ManualHandlingTopic,
                    env.kafkaSm2013AutomaticDigitalHandlingTopic,
                    env.smpapirManualHandlingTopic,
                    env.kafkaSm2013AutomaticPapirmottakTopic,
                    env.sm2013InvalidHandlingTopic
            )
    )
    createListener(applicationState) {
        blockingApplicationLogicReceivedSykmelding(applicationState, kafkaconsumerRecievedSykmelding, database)
    }

    val kafkaconsumerBehandlingsutfall = KafkaConsumer<String, String>(consumerProperties)
    kafkaconsumerBehandlingsutfall.subscribe(
            listOf(
                    env.sm2013BehandlingsUtfallTopic
            )
    )
    createListener(applicationState) {
        blockingApplicationLogicBehandlingsutfall(applicationState, kafkaconsumerBehandlingsutfall, database)
    }
}

suspend fun blockingApplicationLogicReceivedSykmelding(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.ready) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it.value())
            val loggingMeta = LoggingMeta(
                    mottakId = receivedSykmelding.navLogId,
                    orgNr = receivedSykmelding.legekontorOrgNr,
                    msgId = receivedSykmelding.msgId,
                    sykmeldingId = receivedSykmelding.sykmelding.id
            )
            handleMessageSykmelding(receivedSykmelding, database, loggingMeta)
        }
        delay(100)
    }
}

suspend fun handleMessageSykmelding(
    receivedSykmelding: ReceivedSykmelding,
    database: Database,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottatt sykmelding SM2013, {}", fields(loggingMeta))

        if (database.connection.erSykmeldingsopplysningerLagret(receivedSykmelding.sykmelding.id)) {
            log.error("Sykmelding med id {} allerede lagret i databasen, {}", receivedSykmelding.sykmelding.id, fields(loggingMeta))
        } else {
            database.connection.opprettSykmeldingsopplysninger(
                    Sykmeldingsopplysninger(
                            id = receivedSykmelding.sykmelding.id,
                            pasientFnr = receivedSykmelding.personNrPasient,
                            pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                            legeFnr = receivedSykmelding.personNrLege,
                            legeAktoerId = receivedSykmelding.sykmelding.behandler.aktoerId,
                            mottakId = receivedSykmelding.navLogId,
                            legekontorOrgNr = receivedSykmelding.legekontorOrgNr,
                            legekontorHerId = receivedSykmelding.legekontorHerId,
                            legekontorReshId = receivedSykmelding.legekontorReshId,
                            epjSystemNavn = receivedSykmelding.sykmelding.avsenderSystem.navn,
                            epjSystemVersjon = receivedSykmelding.sykmelding.avsenderSystem.versjon,
                            mottattTidspunkt = receivedSykmelding.mottattDato,
                            tssid = receivedSykmelding.tssid
                    )
            )
            database.connection.opprettSykmeldingsdokument(
                    Sykmeldingsdokument(
                            id = receivedSykmelding.sykmelding.id,
                            sykmelding = receivedSykmelding.sykmelding
                    )
            )

            database.registerStatus(SykmeldingStatusEvent(receivedSykmelding.sykmelding.id, receivedSykmelding.mottattDato, StatusEvent.APEN))

            log.info("Sykmelding SM2013 lagret i databasen, {}", fields(loggingMeta))
            MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}

suspend fun blockingApplicationLogicBehandlingsutfall(
    applicationState: ApplicationState,
    kafkaconsumer: KafkaConsumer<String, String>,
    database: Database
) {
    while (applicationState.ready) {
        kafkaconsumer.poll(Duration.ofMillis(0)).forEach {
            val sykmeldingsid = it.key()
            val validationResult: ValidationResult = objectMapper.readValue(it.value())
            val loggingMeta = LoggingMeta(
                    mottakId = "",
                    orgNr = "",
                    msgId = "",
                    sykmeldingId = sykmeldingsid
            )
            handleMessageBehandlingsutfall(validationResult, sykmeldingsid, database, loggingMeta)
        }
        delay(100)
    }
}

suspend fun handleMessageBehandlingsutfall(
    validationResult: ValidationResult,
    sykmeldingsid: String,
    database: Database,
    loggingMeta: LoggingMeta
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottatt behandlingsutfall, {}", fields(loggingMeta))

        if (database.connection.erBehandlingsutfallLagret(sykmeldingsid)) {
            log.error(
                    "Behandlingsutfall for sykmelding med id {} er allerede lagret i databasen, {}", fields(loggingMeta)
            )
        } else {
            database.connection.opprettBehandlingsutfall(
                    Behandlingsutfall(
                            id = sykmeldingsid,
                            behandlingsutfall = validationResult
                    )
            )
            log.info("Behandlingsutfall lagret i databasen, {}", fields(loggingMeta))
        }
    }
}
