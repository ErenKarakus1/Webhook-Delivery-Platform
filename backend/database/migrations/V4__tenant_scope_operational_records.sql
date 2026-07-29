ALTER TABLE delivery.delivery_attempts
    ADD COLUMN tenant_id UUID;

UPDATE delivery.delivery_attempts attempts
SET tenant_id = events.tenant_id
FROM ingestion.events events
WHERE attempts.event_id = events.id
  AND attempts.tenant_id IS NULL;

ALTER TABLE delivery.delivery_attempts
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_delivery_attempts_tenant
        FOREIGN KEY (tenant_id) REFERENCES gateway.tenants(id);

CREATE INDEX idx_delivery_attempts_tenant_id
    ON delivery.delivery_attempts (tenant_id, attempted_at DESC);

ALTER TABLE scheduler.retry_queue
    ADD COLUMN tenant_id UUID;

UPDATE scheduler.retry_queue retries
SET tenant_id = events.tenant_id
FROM ingestion.events events
WHERE retries.event_id = events.id
  AND retries.tenant_id IS NULL;

ALTER TABLE scheduler.retry_queue
    ALTER COLUMN tenant_id SET NOT NULL,
    ADD CONSTRAINT fk_retry_queue_tenant
        FOREIGN KEY (tenant_id) REFERENCES gateway.tenants(id);

CREATE INDEX idx_retry_queue_tenant_id
    ON scheduler.retry_queue (tenant_id, due_at ASC);
