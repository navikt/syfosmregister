package no.nav.syfo.sykmelding.service

internal class BehandligsutfallServiceTest {
    // TODO Why is this blocking??
    /*
    val testDb = TestDB.database
    val environment = mockkClass(Environment::class)

    init {
        every { environment.applicationName } returns "application"
        every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
        every { environment.okSykmeldingTopic } returns "oksykmeldingtopic"
        every { environment.behandlingsUtfallTopic } returns "behandlingsutfallAiven"
        every { environment.avvistSykmeldingTopic } returns "avvisttopicAiven"
        every { environment.manuellSykmeldingTopic } returns "manuelltopic"
        every { environment.cluster } returns "localhost"
    }

    val kafkaConfig = KafkaTest.setupKafkaConfig()
    val applicationState = ApplicationState(alive = true, ready = true)
    val consumerProperties =
        kafkaConfig.toConsumerConfig(
            "${environment.applicationName}-consumer",
            valueDeserializer = StringDeserializer::class,
        )
    val producerProperties =
        kafkaConfig.toProducerConfig(
            "${environment.applicationName}-consumer",
            valueSerializer = JacksonKafkaSerializer::class,
        )
    val behandlingsutfallKafkaProducer = KafkaProducer<String, ValidationResult>(producerProperties)
    val behandlingsutfallKafkaConsumerAiven =
        spyk(KafkaConsumer<String, String>(consumerProperties))
    val behandlingsutfallService =
        BehandlingsutfallService(
            applicationState = applicationState,
            database = testDb,
            env = environment,
            kafkaAivenConsumer = behandlingsutfallKafkaConsumerAiven,
        )
    val tombstoneProducer = KafkaFactory.getTombstoneProducer(environment, consumerProperties)

    @BeforeEach
    fun beforeTest() {
        every { environment.applicationName } returns "application"
        every { environment.mottattSykmeldingKafkaTopic } returns "mottatttopic"
        every { environment.okSykmeldingTopic } returns "oksykmeldingtopic"
        every { environment.behandlingsUtfallTopic } returns "behandlingsutfallAiven"
        every { environment.avvistSykmeldingTopic } returns "avvisttopicAiven"
        every { environment.manuellSykmeldingTopic } returns "manuelltopic"
    }

    @AfterEach
    fun afterTest() {
        testDb.connection.dropData()
    }

    companion object {
        @AfterAll
        @JvmStatic
        internal fun tearDown() {
            TestDB.stop()
        }
    }

    @Test
    internal fun `Test BehandlingsuftallService Should read behandlingsutfall from topic and save in db`() {
        val validationResult = ValidationResult(Status.OK, emptyList())
        behandlingsutfallKafkaProducer.send(
            ProducerRecord(
                environment.behandlingsUtfallTopic,
                "1",
                validationResult,
            ),
        )
        every { behandlingsutfallKafkaConsumerAiven.poll(any<Duration>()) } answers
            {
                val cr = callOriginal()
                if (!cr.isEmpty) {
                    applicationState.ready = false
                }
                cr
            }

        runBlocking {
            behandlingsutfallService.start()

            val behandlingsutfall = testDb.connection.erBehandlingsutfallLagret("1")
            behandlingsutfall shouldBeEqualTo true
        }
    }

    @Test
    internal fun `Test BehandlingsuftallService Should handle tombstone`() {
        runBlocking {
            tombstoneProducer.tombstoneSykmelding("1")
            every { behandlingsutfallKafkaConsumerAiven.poll(any<Duration>()) } answers
                {
                    val cr = callOriginal()
                    if (!cr.isEmpty) {
                        applicationState.ready = false
                    }
                    cr
                }

            behandlingsutfallService.start()

            val behandlingsutfall = testDb.connection.erBehandlingsutfallLagret("1")
            behandlingsutfall shouldBeEqualTo false
        }
    }

     */
}
