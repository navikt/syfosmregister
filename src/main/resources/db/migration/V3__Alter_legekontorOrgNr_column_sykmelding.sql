ALTER TABLE Sykmelding
ALTER COLUMN legekontorOrgNr DROP NOT NULL;

ALTER TABLE Sykmelding
ADD legekontorHerId VARCHAR(50) NULL;

ALTER TABLE Sykmelding
ADD legekontorReshId VARCHAR(50) NULL;