const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:8080";
const adminApiKey = process.env.ADMIN_API_KEY ?? "local-admin-key";
const webhookUrl = process.env.WEBHOOK_URL ?? "https://example.com/webhooks";
const existingTenantId = process.env.TENANT_ID;
const existingApiKey = process.env.API_KEY;

async function request(method, path, { headers = {}, body } = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${method} ${path} failed: ${response.status} ${response.statusText} ${text}`);
  }

  if (response.status === 204) {
    return undefined;
  }

  return response.json();
}

console.log(`Smoke test against ${apiBaseUrl}`);

let tenantId;
let tenantHeaders;
let eventType = "smoke.test";
let endpoint;
let subscription;

if (existingTenantId && existingApiKey) {
  tenantId = existingTenantId;
  tenantHeaders = { "X-API-Key": existingApiKey };
  eventType = process.env.EVENT_TYPE ?? "order.created";
  console.log(`Using existing tenant ${tenantId}`);
} else {
  const tenant = await request("POST", "/tenants", {
    headers: { "X-Admin-Key": adminApiKey },
    body: { name: `Smoke test tenant ${new Date().toISOString()}` },
  });
  tenantId = tenant.id;
  console.log(`Created tenant ${tenant.id}`);

  const apiKeyResponse = await request("POST", `/tenants/${tenant.id}/api-keys`, {
    headers: { "X-Admin-Key": adminApiKey },
    body: { name: "Smoke test key" },
  });
  tenantHeaders = { "X-API-Key": apiKeyResponse.apiKey };
  console.log(`Created tenant API key ${apiKeyResponse.keyPrefix}...`);

  endpoint = await request("POST", `/tenants/${tenant.id}/endpoints`, {
    headers: tenantHeaders,
    body: { url: webhookUrl },
  });
  console.log(`Created endpoint ${endpoint.id}`);

  subscription = await request("POST", `/tenants/${tenant.id}/subscriptions`, {
    headers: tenantHeaders,
    body: { endpointId: endpoint.id, eventType },
  });
  console.log(`Created subscription ${subscription.id}`);
}

const event = await request("POST", `/tenants/${tenantId}/events`, {
  headers: {
    ...tenantHeaders,
    "Idempotency-Key": `smoke-${crypto.randomUUID()}`,
  },
  body: {
    eventType,
    payload: {
      source: "scripts/smoke-test.mjs",
      createdAt: new Date().toISOString(),
    },
  },
});
console.log(`Ingested event ${event.eventId}, delivery jobs published: ${event.deliveryJobsPublished}`);

const [events, subscriptions, endpoints] = await Promise.all([
  request("GET", `/tenants/${tenantId}/events`, { headers: tenantHeaders }),
  request("GET", `/tenants/${tenantId}/subscriptions`, { headers: tenantHeaders }),
  request("GET", `/tenants/${tenantId}/endpoints`, { headers: tenantHeaders }),
]);

if (!events.some((item) => item.id === event.eventId)) {
  throw new Error("Created event was not returned by the events API.");
}
if (subscription && !subscriptions.some((item) => item.id === subscription.id)) {
  throw new Error("Created subscription was not returned by the subscriptions API.");
}
if (endpoint && !endpoints.some((item) => item.id === endpoint.id)) {
  throw new Error("Created endpoint was not returned by the endpoints API.");
}
if (existingTenantId && subscriptions.length === 0) {
  throw new Error("Existing tenant has no subscriptions.");
}
if (existingTenantId && endpoints.length === 0) {
  throw new Error("Existing tenant has no endpoints.");
}

console.log("Smoke test passed.");
