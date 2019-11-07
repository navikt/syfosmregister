insert into sykmeldingstatus (sykmelding_id, event_timestamp, event)
select id, mottatt_tidspunkt, 'APEN' from sykmeldingsopplysninger on CONFLICT DO NOTHING;

insert into sykmeldingstatus (sykmelding_id, event_timestamp, event)
select id, bekreftet_dato, 'BEKREFTET' from sykmeldingsmetadata where bekreftet_dato is not null on CONFLICT DO NOTHING;
