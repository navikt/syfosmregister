CREATE TABLE arbeidsgiver
(
    sykmelding_id        VARCHAR   PRIMARY KEY REFERENCES sykmeldingsopplysninger (id),
    orgnummer            VARCHAR   NOT NULL,
    juridisk_orgnummer   VARCHAR,
    navn                 VARCHAR   NOT NULL
);
