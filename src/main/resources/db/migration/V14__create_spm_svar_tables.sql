CREATE TABLE sporsmal
(
    id          INT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shortName   VARCHAR   NOT NULL,
    tekst       VARCHAR   NOT NULL
);

CREATE TABLE svar
(
    id              INT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sykmelding_id   VARCHAR   NOT NULL,
    sporsmal_id     INT       NOT NULL,
    svartype        VARCHAR   NOT NULL,
    svar            VARCHAR   NOT NULL,

    FOREIGN KEY (sporsmal_id) REFERENCES sporsmal (id)
);
