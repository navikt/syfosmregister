CREATE TABLE sykmeldingstatus
(
    sykmelding_id   VARCHAR(64) NOT NULL,
    event_timestamp TIMESTAMP   NOT NULL,
    event           VARCHAR,

    CONSTRAINT sykmeldingstatus_pk PRIMARY KEY (sykmelding_id, event_timestamp)
);
