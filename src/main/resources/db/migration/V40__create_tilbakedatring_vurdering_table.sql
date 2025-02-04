create table tilbakedatert_vurdering (
    sykmelding_id VARCHAR PRIMARY KEY ,
    fom DATE not null,
    tom DATE null,
    genereringstidspunkt Date not null,
    ettersending_av VARCHAR null,
    forlengelse_av varchar null,
    syketilfellet_startdato VARCHAR null,
    arbeidsgiverperiode BOOLEAN null,
    diagnose_system VARCHAR null,
    diagnose_kode VARCHAR null,
    spesialisthelsetjenesten BOOLEAN null,
    opprettet timestamp with time zone not null,
    vurdering VARCHAR not null
);

create index tilbakedatert_vurdering_ettersending_av_idx on tilbakedatert_vurdering(ettersending_av);
create index tilbakedatert_vurdering_forlengelse_av_idx on tilbakedatert_vurdering(forlengelse_av);
create index tilbakedatert_vurdering_opprettet_av_idx on tilbakedatert_vurdering(opprettet);
