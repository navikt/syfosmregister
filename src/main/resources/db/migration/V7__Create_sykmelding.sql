DROP TABLE Sykmelding;

CREATE TABLE Sykmelding (
  id                     SERIAL PRIMARY KEY,
  pasientfnr             VARCHAR(50) NOT NULL,
  pasientaktorid         VARCHAR(50) NOT NULL,
  legeidfnr              VARCHAR(50) NOT NULL,
  legeaktorid            VARCHAR(50) NOT NULL,
  mottakid               VARCHAR(50) NOT NULL,
  legekontororgnr        VARCHAR(50) NOT NULL,
  legekontorherid        VARCHAR(50) NOT NULL,
  legekontorreshid       VARCHAR(50) NOT NULL,
  legekontororgname      VARCHAR(50) NOT NULL,
  epjsystem              VARCHAR(50) NOT NULL,
  epjversjon             VARCHAR(50) NOT NULL,
  mottatttidspunkt       TIMESTAMP NOT NULL,
  sykemelding            jsonb NOT NULL,
);