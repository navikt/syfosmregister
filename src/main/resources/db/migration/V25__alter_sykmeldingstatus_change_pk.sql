alter table sykmeldingstatus drop constraint sykmeldingstatus_pk;
alter table sykmeldingstatus add constraint sykmeldingstatus_pk primary key using index sykmeldingstatus_pk_idx;
alter table sykmeldingstatus alter column event_timestamp drop not null ;
drop index sykmeldingstatus_event_timestamp_idx;
