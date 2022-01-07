package no.nav.syfo.sykmelding.kafka.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.MottattSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusConsumerServiceTest : Spek({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sykmeldingStatusKafkaConsumer = mockkClass(SykmeldingStatusKafkaConsumer::class)
    val applicationState = ApplicationState(alive = true, ready = true)

    beforeEachTest {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
    }
    val sendtSykmeldingKafkaProducer = mockkClass(SendtSykmeldingKafkaProducer::class)
    val bekreftSykmeldingKafkaProducer = mockkClass(BekreftSykmeldingKafkaProducer::class)
    val mottattSykmeldingKafkaProducer = mockk<MottattSykmeldingKafkaProducer>(relaxed = true)
    val tombstoneProducer = mockkClass(type = SykmeldingTombstoneProducer::class, relaxed = true)
    val sykmeldingStatusKafkaConsumerAiven = mockk<SykmeldingStatusKafkaConsumer>(relaxed = true)
    val database = mockk<DatabaseInterface>(relaxed = true)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, mottattSykmeldingKafkaProducer, tombstoneProducer, database)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusKafkaConsumer, sykmeldingStatusKafkaConsumerAiven, applicationState, mottattSykmeldingStatusService)

    describe("Test retry") {
        it("Should retry if error happens") {
            runBlocking {
                val errors = 3
                var invocationsCounter = 0
                every { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
                every { sykmeldingStatusKafkaConsumer.unsubscribe() } returns Unit
                every { sykmeldingStatusKafkaConsumer.commitSync() } returns Unit
                every { sykmeldingStatusKafkaConsumer.subscribe() } returns Unit
                every { sykmeldingStatusKafkaConsumer.poll() } answers {
                    invocationsCounter++
                    when {
                        invocationsCounter > errors -> {
                            applicationState.alive = false
                            applicationState.ready = false
                            emptyList<SykmeldingStatusKafkaMessageDTO>()
                        }
                        else -> throw RuntimeException("Error")
                    }
                }
                sykmeldingStatusConsumerService.start()
                verify(exactly = 3) { sykmeldingStatusKafkaConsumer.unsubscribe() }
                verify(exactly = 4) { sykmeldingStatusKafkaConsumer.subscribe() }
                verify(exactly = 4) { sykmeldingStatusKafkaConsumer.poll() }
            }
        }
    }
})
