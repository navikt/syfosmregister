alter table arbeidsgiver drop constraint arbeidsgiver_sykmelding_id_fkey;

alter table arbeidsgiver
    add constraint arbeidsgiver_sykmelding_id_fkey
        foreign key (sykmelding_id)
            references sykmeldingsopplysninger (id)
            on delete cascade;
