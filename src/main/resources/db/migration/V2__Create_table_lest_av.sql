CREATE TABLE sykmelding_metadata
(
    sykmeldingsid       CHAR(64) PRIMARY KEY REFERENCES sykmelding (id),
    avvisning_bekreftet TIMESTAMP DEFAULT NULL
);

insert into sykmelding_metadata(sykmeldingsid)
select id
from sykmelding
