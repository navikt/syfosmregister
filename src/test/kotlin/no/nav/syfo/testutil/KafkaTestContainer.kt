package no.nav.syfo.testutil

import org.testcontainers.containers.KafkaContainer

class KafkaTestContainer private constructor() {
    companion object {
        fun getKafkaConatiner(): KafkaContainer {
            return KafkaContainer()
        }
    }
}
