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

#### Requirements

* JDK 17

### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/helse-sykepenger-beregning")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.
Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

``` bash
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run
``` bash
./gradlew shadowJar
```
or on windows 
`gradlew.bat shadowJar`

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

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by navikt/teamsykmelding

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syfosmregister/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
