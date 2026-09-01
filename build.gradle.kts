import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_25


val coroutinesVersion = "1.9.0"
val jacksonVersion = "3.2.2"
val confluentVersion = "8.1.1"
val kluentVersion = "1.73"
val kotestVersion = "6.2.4"
val ktorVersion = "3.5.2"
val logbackVersion = "1.6.3"
val logstashEncoderVersion = "9.0"
val prometheusVersion = "0.16.0"
val postgresVersion = "42.7.7"
val flywayVersion = "10.18.1"
val hikariVersion = "6.0.0"
val vaultJavaDriveVersion = "3.1.0"
val mockkVersion = "1.13.12"
val nimbusdsVersion = "9.41.1"
val testContainerKafkaVersion = "1.20.1"
val caffeineVersion = "3.1.8"
val kotlinVersion = "2.4.10"
val testContainerVersion = "1.21.3"
val ktfmtVersion = "0.56"
val avroVersion = "1.12.0"
val diagnosekoderVersion = "1.2026.0"
val opentelemetryVersion = "2.8.0"

plugins {
    id("application")
    id("com.diffplug.spotless") version "8.10.1"
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "8.3.8"
}
application {
    mainClass.set("no.nav.syfo.BootstrapKt")
}




repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")

    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("tools.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")

    implementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")
    //Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")

}


tasks {

    shadowJar {
        mergeServiceFiles {
             setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
         }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }


    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
