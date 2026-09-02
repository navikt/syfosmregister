create table status_all_spm
(
    sykmelding_id           VARCHAR NOT null primary key,
    alle_spm jsonb          not null
)
