ALTER TABLE management.webhook_endpoints
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE management.webhook_subscriptions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE management.webhook_subscriptions
    DROP CONSTRAINT IF EXISTS webhook_subscriptions_endpoint_id_event_type_key;

DROP INDEX IF EXISTS management.uq_active_endpoint_url_per_tenant;
DROP INDEX IF EXISTS management.idx_webhook_subscriptions_event_type;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_endpoint_url_per_tenant
    ON management.webhook_endpoints (tenant_id, lower(url))
    WHERE is_active = true AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_visible_subscription_event_per_endpoint
    ON management.webhook_subscriptions (endpoint_id, lower(event_type))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_event_type
    ON management.webhook_subscriptions (tenant_id, event_type)
    WHERE is_active = true AND deleted_at IS NULL;
