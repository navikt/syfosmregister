CREATE TABLE sykmelding (
  id SERIAL PRIMARY KEY,
  pasient_fnr CHAR(11) NOT NULL,
  pasient_aktoer_id CHAR(63) NOT NULL,
  lege_fnr CHAR(11) NOT NULL,
  lege_aktoer_id CHAR(63) NOT NULL,
  mottak_id VARCHAR(63) NOT NULL,
  legekontor_org_nr CHAR(9) NOT NULL,
  legekontor_her_id CHAR(63),
  legekontor_resh_id CHAR(63),
  epj_system_navn VARCHAR (63) NOT NULL,
  epj_system_versjon VARCHAR(63) NOT NULL,
  mottatt_tidspunkt TIMESTAMP NOT NULL,
  sykmelding_json jsonb NOT NULL
);
