DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'smregister_publication') THEN
        CREATE PUBLICATION smregister_publication FOR ALL TABLES;
    END IF;
END
$$;
