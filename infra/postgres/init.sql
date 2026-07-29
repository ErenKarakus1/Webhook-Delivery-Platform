CREATE SCHEMA IF NOT EXISTS gateway;
CREATE SCHEMA IF NOT EXISTS delivery;
CREATE SCHEMA IF NOT EXISTS scheduler;

CREATE TABLE IF NOT EXISTS gateway.tenants (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gateway.webhook_endpoints (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    url TEXT NOT NULL,
    secret TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gateway.events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES gateway.tenants(id),
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS delivery.delivery_attempts (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    attempt_number INT NOT NULL,
    status_code INT,
    response_body TEXT,
    error_message TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS scheduler.retry_queue (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    attempt_number INT NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
