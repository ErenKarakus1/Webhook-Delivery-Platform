ALTER TABLE scheduler.retry_queue
    ADD COLUMN payload JSONB;

UPDATE scheduler.retry_queue
SET payload = '{}'::jsonb
WHERE payload IS NULL;

ALTER TABLE scheduler.retry_queue
    ALTER COLUMN payload SET NOT NULL;
