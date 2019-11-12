CREATE TABLE sporsmal
(
    id      VARCHAR(64)   NOT NULL,
    tekst   VARCHAR(255)  NOT NULL
);

CREATE TABLE svar
(
    id              VARCHAR(64)   NOT NULL,
    sykmelding_id   VARCHAR(64)   NOT NULL,
    sporsmal_id     VARCHAR(64)   NOT NULL REFERENCES sporsmal (id),
    svartype        VARCHAR(64)   NOT NULL,
    verdi           VARCHAR(255)  NOT NULL
);
