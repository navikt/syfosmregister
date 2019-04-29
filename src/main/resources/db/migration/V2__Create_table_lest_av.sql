CREATE TABLE sykmelding_metadata (
  sykmeldingsid CHAR(64) PRIMARY KEY REFERENCES sykmelding(id),
  lest_av_bruker TIMESTAMP
);
