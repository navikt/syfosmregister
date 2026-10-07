DROP PUBLICATION IF EXISTS smregister_publication;
SELECT pg_drop_replication_slot(slot_name)
FROM pg_replication_slots
WHERE slot_name = 'smregister_replication';