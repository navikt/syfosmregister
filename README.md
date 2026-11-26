[![Build status](https://github.com/navikt/syfosmregister/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmregister/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# SYFO Sykmeldingregister

Application for persisting sykmelding 2013 in database

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Kafka
* Postgres
* Docker

### :scroll: Prerequisites
* JDK 25
  Make sure you have the Java JDK 25 installed
  You can check which version you have installed using this command:
``` shell
java -version
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run
``` bash
./gradlew shadowJar
```
or on windows 
`gradlew.bat shadowJar`

The application JAR merges Kotlin module metadata and Flyway plugin service descriptors while excluding other duplicate resources.

#### Creating a docker image
Creating a docker image should be as simple as 
``` bash
docker build -t syfosmregister .
```

#### Running a docker image
``` bash
docker run --rm -it -p 8080:8080 syfosmregister
```

#### Starting a local PostgreSQL server

Run
``` bash
docker-compose up
```

### Access to the Postgres database

For information on connecting to dev og prod DB see: [Postgres GCP](https://doc.nais.io/cli/commands/postgres/)

### Upgrading the Gradle wrapper version to latest
``` bash
./gradlew :wrapper --gradle-version latest
```

### Swagger api doc
https://smregister.dev.intern.nav.no/api/v1/docs/

### Contact

This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? 
Please create an [issue](https://github.com/navikt/syfosmregister/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
