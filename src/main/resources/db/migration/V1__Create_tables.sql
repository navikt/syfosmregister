CREATE TABLE Sykmelding (
  id                     SERIAL PRIMARY KEY,
  aktoerIdPasient        VARCHAR(50) NOT NULL,
  aktoerIdLege           VARCHAR(50) NOT NULL,
  navLogId               VARCHAR(50) NOT NULL,
  msgId                  VARCHAR(50) NOT NULL,
  legekontorOrgNr        VARCHAR(50) NOT NULL,
  legekontorOrgName      VARCHAR(50) NOT NULL,
  mottattDato            TIMESTAMP without time zone NOT NULL,
);