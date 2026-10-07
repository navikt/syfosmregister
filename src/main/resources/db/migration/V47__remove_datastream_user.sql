DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'datastream-smregister-user'
    ) THEN
        ALTER DEFAULT PRIVILEGES IN SCHEMA public
            REVOKE SELECT ON TABLES
            FROM "datastream-smregister-user";

        REVOKE SELECT ON ALL TABLES IN SCHEMA public
            FROM "datastream-smregister-user";

        REVOKE USAGE ON SCHEMA public
            FROM "datastream-smregister-user";

        DROP ROLE "datastream-smregister-user";
    END IF;
END
$$;
