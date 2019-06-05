-- Sett opp nye tabeller

CREATE TABLE SYKMELDINGSOPPLYSNINGER
(
    id                 VARCHAR(64) PRIMARY KEY,
    pasient_fnr        VARCHAR(11) NOT NULL,
    pasient_aktoer_id  VARCHAR(63) NOT NULL,
    lege_fnr           VARCHAR(11) NOT NULL,
    lege_aktoer_id     VARCHAR(63) NOT NULL,
    mottak_id          VARCHAR(63) NOT NULL,
    legekontor_org_nr  VARCHAR(20),
    legekontor_her_id  VARCHAR(63),
    legekontor_resh_id VARCHAR(63),
    epj_system_navn    VARCHAR(63) NOT NULL,
    epj_system_versjon VARCHAR(63) NOT NULL,
    mottatt_tidspunkt  TIMESTAMP   NOT NULL,
    tss_id             VARCHAR(63)
);

CREATE TABLE SYKMELDINGSDOKUMENT
(
    id         VARCHAR(64) PRIMARY KEY REFERENCES SYKMELDINGSOPPLYSNINGER,
    sykmelding JSONB NOT NULL
);

CREATE TABLE SYKMELDINGSMETADATA
(
    id             VARCHAR(64) PRIMARY KEY REFERENCES SYKMELDINGSOPPLYSNINGER,
    bekreftet_dato TIMESTAMP DEFAULT NULL
);

CREATE TABLE BEHANDLINGSUTFALL
(
    id                VARCHAR(64) PRIMARY KEY REFERENCES SYKMELDINGSOPPLYSNINGER,
    behandlingsutfall JSONB NOT NULL
);


-- Migrer data til nye tabeller

INSERT INTO SYKMELDINGSOPPLYSNINGER (id, pasient_fnr, pasient_aktoer_id, lege_fnr, lege_aktoer_id, mottak_id,
                                     legekontor_org_nr, legekontor_her_id, legekontor_resh_id, epj_system_navn,
                                     epj_system_versjon, mottatt_tidspunkt, tss_id)
SELECT id,
       pasient_fnr,
       pasient_aktoer_id,
       lege_fnr,
       lege_aktoer_id,
       mottak_id,
       legekontor_org_nr,
       legekontor_her_id,
       legekontor_resh_id,
       epj_system_navn,
       epj_system_versjon,
       mottatt_tidspunkt,
       tss_id
FROM sykmelding;

INSERT INTO SYKMELDINGSDOKUMENT (id, sykmelding)
SELECT id, sykmelding
FROM sykmelding;

INSERT INTO SYKMELDINGSMETADATA (id, bekreftet_dato)
SELECT sykmeldingsid, bekreftet_dato
FROM sykmelding_metadata;

INSERT INTO BEHANDLINGSUTFALL (id, behandlingsutfall)
SELECT id, behandlings_utfall
FROM sykmelding;
