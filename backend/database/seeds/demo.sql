INSERT INTO gateway.tenants (id, name, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Demo tenant', now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO gateway.tenant_api_keys (id, tenant_id, name, key_hash, key_prefix, created_at)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Demo API key',
    'ca6f2e39b2ff141859b18bb5283aadd5093c8ede2869f3656231a054e54bfc22',
    'demo-api',
    now()
)
ON CONFLICT (key_hash) DO NOTHING;

INSERT INTO management.webhook_endpoints (id, tenant_id, url, secret, is_active, created_at)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'https://example.com/webhooks',
    'demo-endpoint-secret',
    true,
    now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO management.webhook_subscriptions (id, tenant_id, endpoint_id, event_type, is_active, created_at)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'order.created',
    true,
    now()
)
ON CONFLICT (endpoint_id, event_type) DO NOTHING;

INSERT INTO ingestion.events (id, tenant_id, event_type, payload, idempotency_key, created_at)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    'order.created',
    '{"orderId":"demo-order-1","total":49.99}'::jsonb,
    'demo-event-1',
    now()
)
ON CONFLICT (tenant_id, idempotency_key) DO NOTHING;

INSERT INTO delivery.delivery_attempts (
    id,
    event_id,
    tenant_id,
    endpoint_id,
    attempt_number,
    status_code,
    response_body,
    attempted_at
)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    1,
    204,
    'demo accepted',
    now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO scheduler.retry_queue (
    id,
    event_id,
    tenant_id,
    endpoint_id,
    attempt_number,
    due_at,
    payload,
    created_at
)
VALUES (
    '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    2,
    now() + interval '5 minutes',
    '{
        "eventId": "55555555-5555-5555-5555-555555555555",
        "tenantId": "11111111-1111-1111-1111-111111111111",
        "endpointId": "33333333-3333-3333-3333-333333333333",
        "eventType": "order.created",
        "url": "https://example.com/webhooks",
        "secret": "demo-endpoint-secret",
        "payload": {"orderId": "demo-order-1", "total": 49.99},
        "attemptNumber": 2,
        "requestedAt": "2026-01-01T00:00:00Z"
    }'::jsonb,
    now()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO delivery.dead_lettered_events (
    id,
    event_id,
    tenant_id,
    endpoint_id,
    event_type,
    attempt_number,
    status_code,
    error_message,
    payload,
    created_at
)
VALUES (
    '88888888-8888-8888-8888-888888888888',
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    '33333333-3333-3333-3333-333333333333',
    'order.failed',
    6,
    500,
    'demo exhausted delivery',
    '{"orderId":"demo-order-2","total":19.99}'::jsonb,
    now()
)
ON CONFLICT (id) DO NOTHING;
