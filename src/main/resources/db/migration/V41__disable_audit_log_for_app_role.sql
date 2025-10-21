DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'smregister-instance')
        THEN
            ALTER USER "smregister-instance" IN DATABASE "smregister" SET pgaudit.log TO 'none';
        END IF;
    END
$$;
