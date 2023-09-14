create table tidligere_arbeidsgiver
(
    sykmelding_id           VARCHAR NOT null primary key,
    tidligere_arbeidsgiver jsonb   not null
)
