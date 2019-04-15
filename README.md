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

### Access to the Postgres database

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

The application uses dynamically generated user / passwords for the database.
To connect to the database one must generate user / password (which lasts for one hour)
as follows:

Install [Vault](https://www.vaultproject.io/downloads.html)


Generate user / password:

```
export VAULT_ADDR=https://vault.adeo.no USER=NAV_IDENT
vault login -method=oidc

```

Preprod credentials:

```
vault read postgresql/preprod-fss/creds/syfosmregler-admin

```

Prod credentials:

```
vault read postgresql/prod-fss/creds/syfosmregler-admin

```

The user / password combination can be used to connect to the relevant databases (From developer image ...)
e.g.

```

psql -d $DATABASE_NAME -h $DATABASE_HOST -U $GENERERT_USER_NAME

```

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Kevin Sillerud, `kevin.sillerud@nav.no`
* Anders Østby, `anders.ostby@nav.no`


### For NAV employees
We are available at the Slack channel #barken
