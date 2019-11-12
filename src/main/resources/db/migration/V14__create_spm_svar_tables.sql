CREATE TABLE sporsmal
(
    id      VARCHAR(64)   NOT NULL PRIMARY KEY,
    tekst   VARCHAR(255)  NOT NULL
);

CREATE TABLE svar
(
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    sykmelding_id   VARCHAR(64)   NOT NULL,
    sporsmal_id     VARCHAR(64)   NOT NULL,
    svartype        VARCHAR(64)   NOT NULL,
    verdi           VARCHAR(255)  NOT NULL,

    FOREIGN KEY (sporsmal_id) REFERENCES sporsmal (id)
);
