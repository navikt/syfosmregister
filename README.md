# SYFO Sykemeldings register

Application for persisting sykmelding 2013 i database

## Technologies used
* Kotlin
* Ktor
* Gradle
* Spek
* Kafka
* Mq
* Vault
* Postgres

## Getting started
## Running locally

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t syfosmregister .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 syfosmregister`

#### Starting a local PostgreSQL server

Run `docker-compose up`.

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Kevin Sillerud, `kevin.sillerud@nav.no`
* Anders Østby, `anders.ostby@nav.no`


### For NAV employees
We are available at the Slack channel #barken
