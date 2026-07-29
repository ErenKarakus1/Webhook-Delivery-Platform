CREATE TABLE delivery.dead_lettered_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES ingestion.events(id),
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    endpoint_id UUID NOT NULL REFERENCES management.webhook_endpoints(id),
    event_type TEXT NOT NULL,
    attempt_number INT NOT NULL,
    status_code INT,
    error_message TEXT,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dead_lettered_events_tenant_id
    ON delivery.dead_lettered_events (tenant_id, created_at DESC);
