
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'smregister-instance')
        THEN
            alter user "smregister-instance" with replication;
        END IF;
    END
$$;
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'datastream-smregister-user')
        THEN
            alter user "datastream-smregister-user" with replication;
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream-smregister-user";
            GRANT USAGE ON SCHEMA public TO "datastream-smregister-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream-smregister-user";
        END IF;
    END
$$;
