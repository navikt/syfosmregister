package no.nav.syfo.sykmelding.kafka.producer

internal class BekreftSykmeldingKafkaProducerTest {
    // TODO Why is this blocking??
    /*
    val environment = mockkClass(Environment::class)

    init {
        every { environment.applicationName } returns
            "${BehandligsutfallServiceTest::class.simpleName}-application"
        every { environment.bekreftSykmeldingKafkaTopic } returns
            "${environment.applicationName}-syfo-bekreft-sykmelding"
        every { environment.cluster } returns "localhost"
    }

    val kafkaconfig = KafkaTest.setupKafkaConfig()
    val kafkaProducer = KafkaFactory.getBekreftetSykmeldingKafkaProducer(environment, kafkaconfig)
    val properties =
        kafkaconfig
            .toConsumerConfig(
                "${environment.applicationName}-consumer",
                JacksonKafkaDeserializer::class
            )
            .also { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
    val kafkaTestReader = KafkaTestReader<SykmeldingKafkaMessage>()
    val kafkaConsumer =
        KafkaConsumer(
            properties,
            StringDeserializer(),
            JacksonKafkaDeserializer(SykmeldingKafkaMessage::class)
        )

    @BeforeEach
    fun setup() {
        kafkaConsumer.subscribe(listOf("${environment.applicationName}-syfo-bekreft-sykmelding"))
    }

    @Test
    internal fun `Test kafka Should bekreft value to topic`() {
        runBlocking {
            kafkaProducer.sendSykmelding(
                SykmeldingKafkaMessage(
                    getArbeidsgiverSykmelding("1"),
                    getKafkaMetadata("1"),
                    getSykmeldingStatusEvent("1"),
                ),
            )
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages["1"] shouldNotBe null
        }
    }

    @Test
    internal fun `Test kafka Should tombstone`() {
        runBlocking {
            kafkaProducer.tombstoneSykmelding("1")
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 1)
            messages.containsKey("1") shouldBe true
            messages["1"] shouldBe null
        }
    }

    @Test
    internal fun `Test kafka should send Bekreft then tombstone`() {
        runBlocking {
            kafkaProducer.sendSykmelding(
                SykmeldingKafkaMessage(
                    getArbeidsgiverSykmelding("2"),
                    getKafkaMetadata("2"),
                    getSykmeldingStatusEvent("2"),
                ),
            )
            kafkaProducer.tombstoneSykmelding("2")
            val messages = kafkaTestReader.getMessagesFromTopic(kafkaConsumer, 2)
            messages.containsKey("2") shouldBe true
            messages["2"] shouldBe null
        }
    }

     */
}
