CREATE SCHEMA IF NOT EXISTS gateway;
CREATE SCHEMA IF NOT EXISTS ingestion;
CREATE SCHEMA IF NOT EXISTS management;
CREATE SCHEMA IF NOT EXISTS delivery;
CREATE SCHEMA IF NOT EXISTS scheduler;

CREATE TABLE IF NOT EXISTS gateway.tenants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS management.webhook_endpoints (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    url TEXT NOT NULL,
    secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS management.webhook_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    endpoint_id UUID NOT NULL REFERENCES management.webhook_endpoints(id),
    event_type TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (endpoint_id, event_type)
);

CREATE TABLE IF NOT EXISTS ingestion.events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS delivery.delivery_attempts (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES ingestion.events(id),
    endpoint_id UUID NOT NULL REFERENCES management.webhook_endpoints(id),
    attempt_number INT NOT NULL,
    status_code INT,
    response_body TEXT,
    error_message TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS scheduler.retry_queue (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES ingestion.events(id),
    endpoint_id UUID NOT NULL REFERENCES management.webhook_endpoints(id),
    attempt_number INT NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_event_type
    ON management.webhook_subscriptions (tenant_id, event_type)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_retry_queue_due_at
    ON scheduler.retry_queue (due_at);
