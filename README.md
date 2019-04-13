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

### Tilgang til Postgres databasen

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

Applikasjonen benytter seg av dynamisk genererte bruker/passord til database.
For å koble seg til databasen må man genere bruker/passord(som varer i en time)
på følgende måte:

Installere [Vault](https://www.vaultproject.io/downloads.html)

Generere bruker/passord: 

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

Bruker/passord kombinasjonen kan brukes til å koble seg til de aktuelle databasene(Fra utvikler image...)
F.eks

```

psql -d $DATABASE_NAME -h $DATABASE_HOST -U $GENERERT_BRUKER_NAVN

```

#### Starting a local PostgreSQL server

Run `docker-compose up`.

## Contact us
### Code/project related questions can be sent to
* Joakim Kartveit, `joakim.kartveit@nav.no`
* Kevin Sillerud, `kevin.sillerud@nav.no`
* Anders Østby, `anders.ostby@nav.no`


### For NAV employees
We are available at the Slack channel #barken
