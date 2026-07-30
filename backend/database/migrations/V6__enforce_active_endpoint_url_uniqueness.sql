WITH ranked_endpoints AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY tenant_id, lower(url)
            ORDER BY created_at DESC, id DESC
        ) AS row_number
    FROM management.webhook_endpoints
    WHERE is_active = true
)
UPDATE management.webhook_endpoints endpoints
SET is_active = false
FROM ranked_endpoints ranked
WHERE endpoints.id = ranked.id
  AND ranked.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_endpoint_url_per_tenant
    ON management.webhook_endpoints (tenant_id, lower(url))
    WHERE is_active = true;
