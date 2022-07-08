package no.nav.syfo.sykmelding.kafka.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.mockkStatic
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmelding.kafka.consumer.SykmeldingStatusKafkaConsumer
import no.nav.syfo.sykmelding.kafka.producer.BekreftSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SendtSykmeldingKafkaProducer
import no.nav.syfo.sykmelding.kafka.producer.SykmeldingTombstoneProducer
import no.nav.syfo.sykmelding.status.SykmeldingStatusService

class SykmeldingStatusConsumerServiceTest : FunSpec({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val sykmeldingStatusKafkaConsumer = mockkClass(SykmeldingStatusKafkaConsumer::class)
    val applicationState = ApplicationState(alive = true, ready = true)

    beforeTest {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
    }
    val sendtSykmeldingKafkaProducer = mockkClass(SendtSykmeldingKafkaProducer::class)
    val bekreftSykmeldingKafkaProducer = mockkClass(BekreftSykmeldingKafkaProducer::class)
    val tombstoneProducer = mockkClass(type = SykmeldingTombstoneProducer::class, relaxed = true)
    val database = mockk<DatabaseInterface>(relaxed = true)
    val mottattSykmeldingStatusService = MottattSykmeldingStatusService(sykmeldingStatusService, sendtSykmeldingKafkaProducer, bekreftSykmeldingKafkaProducer, tombstoneProducer, database)
    val sykmeldingStatusConsumerService = SykmeldingStatusConsumerService(sykmeldingStatusKafkaConsumer, applicationState, mottattSykmeldingStatusService, mockk(relaxed = true))

    context("Test retry") {
        test("Should retry if error happens") {
            val errors = 3
            var invocationsCounter = 0
            coEvery { sendtSykmeldingKafkaProducer.sendSykmelding(any()) } returns Unit
            coEvery { sykmeldingStatusKafkaConsumer.unsubscribe() } returns Unit
            coEvery { sykmeldingStatusKafkaConsumer.commitSync() } returns Unit
            coEvery { sykmeldingStatusKafkaConsumer.subscribe() } returns Unit
            coEvery { sykmeldingStatusKafkaConsumer.poll() } answers {
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
            coVerify(exactly = 3) { sykmeldingStatusKafkaConsumer.unsubscribe() }
            coVerify(exactly = 4) { sykmeldingStatusKafkaConsumer.subscribe() }
            coVerify(exactly = 4) { sykmeldingStatusKafkaConsumer.poll() }
        }
    }
})
