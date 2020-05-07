package no.nav.syfo.sykmelding.kafka.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.status.SykmeldingStatusService
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingStatusConsumerServiceTest : Spek({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sykmeldingStatusKafkaConsumer = mockkClass(SykmeldingStatusKafkaConsumer::class)
    val applicationState = ApplicationState(alive = true, ready = true)

    beforeEachTest {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any()) } returns Unit
    }
    val sendtSykmeldingKafkaProducer = mockkClass(SendtSykmeldingKafkaProducer::class)
    val bekreftSykmeldingKafkaProducer = mockkClass(BekreftSykmeldingKafkaProducer::class)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusService, sykmeldingStatusKafkaConsumer, applicationState, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer)

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
