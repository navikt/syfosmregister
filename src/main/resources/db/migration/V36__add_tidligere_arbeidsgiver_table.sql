create table tidligere_arbeidsgiver
(
    sykmeldingId           VARCHAR NOT null primary key,
    tidligere_arbeidsgiver jsonb   not null
)
