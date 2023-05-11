import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.7.0"
val jacksonVersion = "2.15.0"
val confluentVersion = "7.4.0"
val kluentVersion = "1.73"
val ktorVersion = "2.3.0"
val logbackVersion = "1.4.7"
val logstashEncoderVersion = "7.3"
val prometheusVersion = "0.16.0"
val kotestVersion = "5.6.1"
val postgresVersion = "42.6.0"
val flywayVersion = "9.17.0"
val hikariVersion = "5.0.1"
val vaultJavaDriveVersion = "3.1.0"
val smCommonVersion = "1.0.1"
val mockkVersion = "1.13.5"
val nimbusdsVersion = "9.31"
val testContainerKafkaVersion = "1.18.1"
val caffeineVersion = "3.1.6"
val kotlinVersion = "1.8.21"
val swaggerUiVersion = "4.18.2"
val testContainerVersion = "1.18.1"
val commonsCodecVersion = "1.15"
val snakeyamlVersion= "2.0"

plugins {
    id("org.jmailen.kotlinter") version "3.14.0"
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

buildscript {
    dependencies {
    }

}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("commons-codec:commons-codec:$commonsCodecVersion")
    // override transient version 1.10 from io.ktor:ktor-client-apache
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("org.yaml:snakeyaml:$snakeyamlVersion")
    // override transient version 1.32 from io.confluent:kafka-avro-serializer

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")

    //Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")

    swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")
}
swaggerSources {
    create("sykmelding").apply {
        setInputFile(file("api/oas3/syfosmregister-v1.yaml"))
    }
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
        dependsOn(":generateSwaggerUI")
    }

    create("printVersion") {
        println(project.version)
    }

    withType<org.hidetake.gradle.swagger.generator.GenerateSwaggerUI> {
        outputDir = File(buildDir.path + "/resources/main/api")
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        dependsOn(":generateSwaggerUI")
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
        dependsOn("generateSwaggerUI")
    }

    withType<Test> {
        useJUnitPlatform {
        }
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    "check" {
        dependsOn("formatKotlin")
    }
}
