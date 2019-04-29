CREATE TABLE lest_av (
  sykmeldingsid CHAR(64) PRIMARY KEY REFERENCES sykmelding(id),
  lest_av_bruker TIMESTAMP
);
