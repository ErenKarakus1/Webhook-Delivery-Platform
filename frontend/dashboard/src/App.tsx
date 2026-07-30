import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, Bell, Clock, RefreshCcw } from "lucide-react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const ADMIN_API_KEY = import.meta.env.VITE_ADMIN_API_KEY ?? "local-admin-key";
const NODE_VERIFICATION_SNIPPET = [
  'import crypto from "node:crypto";',
  "",
  "function verifyWebhook({ rawBody, secret, signature, timestamp }) {",
  "  const signedPayload = `${timestamp}.${rawBody}`;",
  '  const expected = "sha256=" + crypto',
  '    .createHmac("sha256", secret)',
  "    .update(signedPayload)",
  '    .digest("hex");',
  "",
  "  const expectedBuffer = Buffer.from(expected);",
  "  const signatureBuffer = Buffer.from(signature);",
  "  return expectedBuffer.length === signatureBuffer.length &&",
  "    crypto.timingSafeEqual(expectedBuffer, signatureBuffer);",
  "}",
].join("\n");

type Endpoint = {
  id: string;
  url: string;
  secret: string;
  active: boolean;
  createdAt: string;
};

type Subscription = {
  id: string;
  endpointId: string;
  eventType: string;
  active: boolean;
  createdAt: string;
};

type Event = {
  id: string;
  eventType: string;
  createdAt: string;
};

type Attempt = {
  id: string;
  eventId: string;
  endpointId: string;
  attemptNumber: number;
  statusCode: number | null;
  errorMessage: string | null;
  attemptedAt: string;
};

type Retry = {
  id: string;
  eventId: string;
  endpointId: string;
  attemptNumber: number;
  dueAt: string;
};

type DeadLetteredEvent = {
  id: string;
  eventId: string;
  endpointId: string;
  eventType: string;
  attemptNumber: number;
  statusCode: number | null;
  errorMessage: string | null;
  createdAt: string;
};

type EventIngestionResponse = {
  eventId: string;
  tenantId: string;
  eventType: string;
  deliveryJobsPublished: number;
  duplicate: boolean;
  createdAt: string;
};

function App() {
  const [tenantId, setTenantId] = useState(() => localStorage.getItem("tenantId") ?? "");
  const [apiKey, setApiKey] = useState(() => localStorage.getItem("apiKey") ?? "");
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [retries, setRetries] = useState<Retry[]>([]);
  const [deadLetteredEvents, setDeadLetteredEvents] = useState<DeadLetteredEvent[]>([]);
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
  const [selectedEventAttempts, setSelectedEventAttempts] = useState<Attempt[]>([]);
  const [endpointUrl, setEndpointUrl] = useState("");
  const [subscriptionEndpointId, setSubscriptionEndpointId] = useState("");
  const [subscriptionEventType, setSubscriptionEventType] = useState("order.created");
  const [eventType, setEventType] = useState("order.created");
  const [eventPayload, setEventPayload] = useState('{\n  "orderId": "ord_123",\n  "total": 49.99\n}');
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [endpointLoading, setEndpointLoading] = useState(false);
  const [endpointActionLoading, setEndpointActionLoading] = useState<string | null>(null);
  const [subscriptionLoading, setSubscriptionLoading] = useState(false);
  const [subscriptionActionLoading, setSubscriptionActionLoading] = useState<string | null>(null);
  const [eventLoading, setEventLoading] = useState(false);
  const [setupLoading, setSetupLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sendResult, setSendResult] = useState<string | null>(null);

  const stats = useMemo(() => {
    const delivered = attempts.filter((attempt) => attempt.statusCode && attempt.statusCode >= 200 && attempt.statusCode < 300);
    const successRate = attempts.length === 0 ? "0%" : `${Math.round((delivered.length / attempts.length) * 100)}%`;

    return [
      { label: "Events", value: events.length.toString(), icon: Bell },
      { label: "Success", value: successRate, icon: Activity },
      { label: "Retries", value: retries.length.toString(), icon: Clock },
      { label: "Dead letters", value: deadLetteredEvents.length.toString(), icon: AlertTriangle },
    ];
  }, [attempts, deadLetteredEvents, events, retries]);

  const recentAttempts = selectedEvent ? selectedEventAttempts : attempts.slice(0, 6);
  const deliverySummary = useMemo(() => {
    const delivered = recentAttempts.filter((attempt) => attemptStatus(attempt) === "Delivered").length;
    const failed = recentAttempts.filter((attempt) => attemptStatus(attempt) === "Failed").length;
    const retrying = recentAttempts.filter((attempt) => attemptStatus(attempt) === "Retrying").length;

    return {
      delivered,
      failed,
      retrying,
      total: recentAttempts.length,
    };
  }, [recentAttempts]);

  useEffect(() => {
    localStorage.setItem("tenantId", tenantId);
    localStorage.setItem("apiKey", apiKey);
  }, [apiKey, tenantId]);

  async function loadDashboard(nextTenantId = tenantId, nextApiKey = apiKey) {
    if (!nextTenantId || !nextApiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const headers = { "X-API-Key": nextApiKey };
      const [endpointData, subscriptionData, eventData, attemptData, retryData, deadLetterData] = await Promise.all([
        request<Endpoint[]>(`/tenants/${nextTenantId}/endpoints`, headers),
        request<Subscription[]>(`/tenants/${nextTenantId}/subscriptions`, headers),
        request<Event[]>(`/tenants/${nextTenantId}/events`, headers),
        request<Attempt[]>(`/tenants/${nextTenantId}/attempts`, headers),
        request<Retry[]>(`/tenants/${nextTenantId}/retries`, headers),
        request<DeadLetteredEvent[]>(`/tenants/${nextTenantId}/dead-lettered-events`, headers),
      ]);

      setEndpoints(endpointData);
      setSubscriptions(subscriptionData);
      setSubscriptionEndpointId((current) => current || endpointData[0]?.id || "");
      setEvents(eventData);
      setAttempts(attemptData);
      setRetries(retryData);
      setDeadLetteredEvents(deadLetterData);
      setSelectedEvent(eventData[0] ?? null);
      setSelectedEventAttempts(eventData[0] ? attemptData.filter((attempt) => attempt.eventId === eventData[0].id) : []);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load dashboard data.");
    } finally {
      setLoading(false);
    }
  }

  async function loadEventDetail(eventId: string) {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setDetailLoading(true);
    setError(null);

    try {
      const headers = { "X-API-Key": apiKey };
      const [eventData, attemptData] = await Promise.all([
        request<Event>(`/tenants/${tenantId}/events/${eventId}`, headers),
        request<Attempt[]>(`/tenants/${tenantId}/attempts?eventId=${eventId}`, headers),
      ]);
      setSelectedEvent(eventData);
      setSelectedEventAttempts(attemptData);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load event detail.");
    } finally {
      setDetailLoading(false);
    }
  }

  function submitCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadDashboard();
  }

  async function createEndpoint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }
    if (!endpointUrl.trim()) {
      setError("Endpoint URL is required.");
      return;
    }

    const normalizedUrl = endpointUrl.trim();
    if (endpoints.some((endpoint) => endpoint.url.toLowerCase() === normalizedUrl.toLowerCase())) {
      setError("That endpoint URL already exists. Use the existing endpoint or reactivate it.");
      return;
    }

    setEndpointLoading(true);
    setError(null);

    try {
      const endpoint = await request<Endpoint>(`/tenants/${tenantId}/endpoints`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({ url: normalizedUrl }),
      });
      setEndpoints((current) => [endpoint, ...current]);
      setSubscriptionEndpointId((current) => current || endpoint.id);
      setEndpointUrl("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create endpoint.");
    } finally {
      setEndpointLoading(false);
    }
  }

  async function setEndpointActive(endpoint: Endpoint, active: boolean) {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setEndpointActionLoading(endpoint.id);
    setError(null);

    try {
      const updatedEndpoint = await request<Endpoint>(
        `/tenants/${tenantId}/endpoints/${endpoint.id}/${active ? "activate" : "deactivate"}`,
        { "X-API-Key": apiKey },
        { method: "PATCH" },
      );
      setEndpoints((current) => current.map((item) => item.id === updatedEndpoint.id ? updatedEndpoint : item));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not update endpoint.");
    } finally {
      setEndpointActionLoading(null);
    }
  }

  async function createSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }
    if (!subscriptionEndpointId) {
      setError("Select an endpoint for the subscription.");
      return;
    }
    if (!subscriptionEventType.trim()) {
      setError("Subscription event type is required.");
      return;
    }

    const normalizedEventType = subscriptionEventType.trim();
    if (subscriptions.some((subscription) => (
      subscription.endpointId === subscriptionEndpointId
      && subscription.eventType.toLowerCase() === normalizedEventType.toLowerCase()
    ))) {
      setError("That endpoint already has a subscription for this event type. Use the existing subscription or reactivate it.");
      return;
    }

    setSubscriptionLoading(true);
    setError(null);

    try {
      const subscription = await request<Subscription>(`/tenants/${tenantId}/subscriptions`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({
          endpointId: subscriptionEndpointId,
          eventType: normalizedEventType,
        }),
      });
      setSubscriptions((current) => [subscription, ...current]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create subscription.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  async function setSubscriptionActive(subscription: Subscription, active: boolean) {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setSubscriptionActionLoading(subscription.id);
    setError(null);

    try {
      const updatedSubscription = await request<Subscription>(
        `/tenants/${tenantId}/subscriptions/${subscription.id}/${active ? "activate" : "deactivate"}`,
        { "X-API-Key": apiKey },
        { method: "PATCH" },
      );
      setSubscriptions((current) => current.map((item) => item.id === updatedSubscription.id ? updatedSubscription : item));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not update subscription.");
    } finally {
      setSubscriptionActionLoading(null);
    }
  }

  async function ingestEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }
    if (!eventType.trim()) {
      setError("Event type is required.");
      return;
    }

    let payload: unknown;
    try {
      payload = JSON.parse(eventPayload);
    } catch {
      setError("Event payload must be valid JSON.");
      return;
    }

    setEventLoading(true);
    setError(null);
    setSendResult(null);

    try {
      const headers: Record<string, string> = { "X-API-Key": apiKey };
      if (idempotencyKey.trim()) {
        headers["Idempotency-Key"] = idempotencyKey.trim();
      }
      const response = await request<EventIngestionResponse>(`/tenants/${tenantId}/events`, headers, {
        method: "POST",
        body: JSON.stringify({ eventType: eventType.trim(), payload }),
      });
      const createdEvent = {
        id: response.eventId,
        eventType: response.eventType,
        createdAt: response.createdAt,
      };
      setEvents((current) => [createdEvent, ...current.filter((item) => item.id !== createdEvent.id)]);
      setSelectedEvent(createdEvent);
      setSelectedEventAttempts([]);
      setIdempotencyKey("");
      setSendResult(deliveryJobMessage(response.deliveryJobsPublished, response.duplicate));
      await waitForEventAttempts(createdEvent.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not ingest event.");
    } finally {
      setEventLoading(false);
    }
  }

  async function waitForEventAttempts(eventId: string) {
    const headers = { "X-API-Key": apiKey };

    for (let index = 0; index < 6; index += 1) {
      await delay(index === 0 ? 500 : 800);
      const attemptData = await request<Attempt[]>(`/tenants/${tenantId}/attempts?eventId=${eventId}`, headers);
      if (attemptData.length > 0) {
        setSelectedEventAttempts(attemptData);
        setAttempts((current) => mergeAttempts(attemptData, current));
        return;
      }
    }
  }

  async function createTenantAndApiKey() {
    setSetupLoading(true);
    setError(null);
    try {
      const tenant = await request<{ id: string }>("/tenants", { "X-Admin-Key": ADMIN_API_KEY }, {
        method: "POST",
        body: JSON.stringify({ name: "Local tenant" }),
      });
      const apiKeyResponse = await request<{ apiKey: string }>(`/tenants/${tenant.id}/api-keys`, {
        "X-Admin-Key": ADMIN_API_KEY,
      }, {
        method: "POST",
        body: JSON.stringify({ name: "Dashboard local key" }),
      });
      setTenantId(tenant.id);
      setApiKey(apiKeyResponse.apiKey);
      await loadDashboard(tenant.id, apiKeyResponse.apiKey);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create local tenant.");
    } finally {
      setSetupLoading(false);
    }
  }

  return (
    <main className="app">
      <header className="topbar">
        <div>
          <p className="eyebrow">Webhook Delivery Platform</p>
          <h1>Test a webhook delivery.</h1>
        </div>
        <button type="button" onClick={() => void loadDashboard()} disabled={loading}>
          <RefreshCcw size={16} aria-hidden="true" />
          Refresh
        </button>
      </header>

      <form className="connection" onSubmit={submitCredentials}>
        <label>
          <span>Tenant ID</span>
          <input value={tenantId} onChange={(event) => setTenantId(event.target.value)} />
        </label>
        <label>
          <span>API key</span>
          <input type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
        </label>
        <button type="submit" disabled={loading}>Connect</button>
        <button type="button" className="secondary" onClick={createTenantAndApiKey} disabled={setupLoading}>
          Create tenant
        </button>
      </form>

      {error && <div className="error">{error}</div>}

      <section className="stats">
        {stats.map(({ label, value, icon: Icon }) => (
          <article className="stat" key={label}>
            <Icon size={18} aria-hidden="true" />
            <span>{label}</span>
            <strong>{value}</strong>
          </article>
        ))}
      </section>

      <section className="workspace">
        <section className="panel send-panel">
          <PanelHeader title="Send event" meta="Demo payload" />
          <form className="stack-form" onSubmit={ingestEvent}>
            <label>
              <span>Event type</span>
              <input value={eventType} onChange={(event) => setEventType(event.target.value)} />
            </label>
            <label>
              <span>Idempotency key</span>
              <input placeholder="Optional" value={idempotencyKey} onChange={(event) => setIdempotencyKey(event.target.value)} />
            </label>
            <label>
              <span>Payload</span>
              <textarea value={eventPayload} onChange={(event) => setEventPayload(event.target.value)} spellCheck={false} />
            </label>
            <button type="submit" disabled={eventLoading}>Send event</button>
            {sendResult && <p className="result-message">{sendResult}</p>}
          </form>
        </section>

        <section className="panel">
          <PanelHeader title="Recent events" meta={`${events.length} total`} />
          <div className="list">
            {events.slice(0, 8).length > 0 ? events.slice(0, 8).map((event) => (
              <button
                className={`row row-button ${selectedEvent?.id === event.id ? "selected" : ""}`}
                key={event.id}
                onClick={() => void loadEventDetail(event.id)}
                type="button"
              >
                <div>
                  <strong>{event.eventType}</strong>
                  <span>{event.id}</span>
                </div>
                <time>{formatDate(event.createdAt)}</time>
              </button>
            )) : <p className="empty">No events yet.</p>}
          </div>
        </section>

        <section className="panel">
          <PanelHeader title={selectedEvent ? "Selected delivery" : "Delivery attempts"} meta={detailLoading ? "Loading" : `${recentAttempts.length} shown`} />
          {selectedEvent && (
            <div className="selected-event">
              <div>
                <span>Event type</span>
                <strong>{selectedEvent.eventType}</strong>
              </div>
              <div>
                <span>Event ID</span>
                <code>{selectedEvent.id}</code>
              </div>
              <div>
                <span>Delivery jobs</span>
                <strong>{deliverySummary.total}</strong>
              </div>
              <div>
                <span>Delivered</span>
                <strong>{deliverySummary.delivered}</strong>
              </div>
              <div>
                <span>Failed</span>
                <strong>{deliverySummary.failed}</strong>
              </div>
              <div>
                <span>Retrying</span>
                <strong>{deliverySummary.retrying}</strong>
              </div>
            </div>
          )}
          <div className="list">
            {recentAttempts.length > 0 ? recentAttempts.map((attempt) => (
              <article className="row attempt" key={attempt.id}>
                <div>
                  <strong>{attemptStatus(attempt)} to {endpointLabel(attempt.endpointId, endpoints)}</strong>
                  <small>Attempt {attempt.attemptNumber}</small>
                  <span>{attemptDetail(attempt)}</span>
                </div>
                <span className={`status ${attemptStatus(attempt).toLowerCase()}`}>{attemptStatus(attempt)}</span>
                <time>{formatDate(attempt.attemptedAt)}</time>
              </article>
            )) : (
              <p className="empty">
                {selectedEvent ? "No matching active subscription for this event type, or delivery has not started yet." : "No delivery attempts yet."}
              </p>
            )}
          </div>
        </section>

        <section className="panel setup-panel">
          <PanelHeader title="Setup" meta={`${endpoints.length} endpoints, ${subscriptions.length} subscriptions`} />
          <div className="setup-grid">
            <div>
              <h2>Endpoint</h2>
              <form className="inline-form" onSubmit={createEndpoint}>
                <input
                  aria-label="Endpoint HTTPS URL"
                  placeholder="https://example.com/webhooks"
                  value={endpointUrl}
                  onChange={(event) => setEndpointUrl(event.target.value)}
                />
                <button type="submit" disabled={endpointLoading}>Add</button>
              </form>
              <SimpleList empty="No endpoints configured.">
                {endpoints.slice(0, 4).map((endpoint) => (
                  <article className="compact-row" key={endpoint.id}>
                    <div>
                      <strong>{endpoint.active ? "Active" : "Inactive"}</strong>
                      <small>{endpoint.active ? "Receives matching events" : "Kept for history; no new deliveries"}</small>
                      <span>{endpoint.url}</span>
                      <details className="secret-detail">
                        <summary>Show signing secret</summary>
                        <code>{endpoint.secret}</code>
                        <details className="verification-detail">
                          <summary>Verification example</summary>
                          <dl className="verification-list">
                            <div>
                              <dt>Signed payload</dt>
                              <dd>
                                <code>timestamp + "." + rawBody</code>
                              </dd>
                            </div>
                            <div>
                              <dt>Algorithm</dt>
                              <dd>HMAC-SHA256 with the endpoint secret</dd>
                            </div>
                            <div>
                              <dt>Compare against</dt>
                              <dd>
                                <code>X-Webhook-Signature</code>
                              </dd>
                            </div>
                          </dl>
                          <pre>
                            <code>{NODE_VERIFICATION_SNIPPET}</code>
                          </pre>
                          <p className="verification-note">
                            Use the raw request body before JSON parsing, plus the{" "}
                            <code>X-Webhook-Timestamp</code> header.
                          </p>
                        </details>
                      </details>
                    </div>
                    <button
                      aria-label={endpoint.active ? "Deactivate endpoint" : "Activate endpoint"}
                      className="secondary compact-action"
                      disabled={endpointActionLoading === endpoint.id}
                      onClick={() => void setEndpointActive(endpoint, !endpoint.active)}
                      type="button"
                    >
                      {endpoint.active ? "Deactivate" : "Activate"}
                    </button>
                  </article>
                ))}
              </SimpleList>
            </div>
            <div>
              <h2>Subscription</h2>
              <form className="inline-form subscription-form" onSubmit={createSubscription}>
                <select
                  aria-label="Subscription endpoint"
                  value={subscriptionEndpointId}
                  onChange={(event) => setSubscriptionEndpointId(event.target.value)}
                >
                  <option value="">Select endpoint</option>
                  {endpoints.map((endpoint) => (
                    <option key={endpoint.id} value={endpoint.id}>{endpoint.url}</option>
                  ))}
                </select>
                <input
                  aria-label="Subscription event type"
                  value={subscriptionEventType}
                  onChange={(event) => setSubscriptionEventType(event.target.value)}
                />
                <button type="submit" disabled={subscriptionLoading}>Add</button>
              </form>
              <SimpleList empty="No subscriptions configured.">
                {subscriptions.slice(0, 4).map((subscription) => (
                  <article className="compact-row" key={subscription.id}>
                    <div>
                      <strong>{subscription.active ? "Active" : "Inactive"} / {subscription.eventType}</strong>
                      <small>{subscription.active ? "Publishes delivery jobs" : "Kept for history; no new jobs"}</small>
                      <span>{endpointLabel(subscription.endpointId, endpoints)}</span>
                    </div>
                    <button
                      aria-label={subscription.active ? "Deactivate subscription" : "Activate subscription"}
                      className="secondary compact-action"
                      disabled={subscriptionActionLoading === subscription.id}
                      onClick={() => void setSubscriptionActive(subscription, !subscription.active)}
                      type="button"
                    >
                      {subscription.active ? "Deactivate" : "Activate"}
                    </button>
                  </article>
                ))}
              </SimpleList>
            </div>
          </div>
        </section>
      </section>

      <details className="advanced">
        <summary>Advanced queues</summary>
        <div className="advanced-grid">
          <Queue title="Retry queue" empty="No pending retries.">
            {retries.slice(0, 5).map((retry) => (
              <article className="compact-row" key={retry.id}>
                <div>
                  <strong>Attempt {retry.attemptNumber}</strong>
                  <span>{retry.eventId}</span>
                </div>
                <time>{formatDate(retry.dueAt)}</time>
              </article>
            ))}
          </Queue>
          <Queue title="Dead letters" empty="No dead-lettered events.">
            {deadLetteredEvents.slice(0, 5).map((event) => (
              <article className="compact-row" key={event.id}>
                <div>
                  <strong>{event.eventType}</strong>
                  <span>{event.errorMessage ?? event.eventId}</span>
                </div>
                <span className="status failed">{event.statusCode ?? "Failed"}</span>
              </article>
            ))}
          </Queue>
        </div>
      </details>
    </main>
  );
}

function PanelHeader({ meta, title }: { meta?: string; title: string }) {
  return (
    <div className="panel-header">
      <h2>{title}</h2>
      {meta && <span>{meta}</span>}
    </div>
  );
}

function SimpleList({ children, empty }: { children: React.ReactNode; empty: string }) {
  const items = React.Children.toArray(children).filter(Boolean);
  return <div className="simple-list">{items.length > 0 ? items : <p className="empty">{empty}</p>}</div>;
}

function Queue({ children, empty, title }: { children: React.ReactNode; empty: string; title: string }) {
  return (
    <section className="queue">
      <h2>{title}</h2>
      <SimpleList empty={empty}>{children}</SimpleList>
    </section>
  );
}

async function request<T>(path: string, headers: Record<string, string>, init: RequestInit & { headers?: Record<string, string> } = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...headers,
      ...init.headers,
    },
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

function attemptStatus(attempt: Attempt) {
  if (attempt.statusCode && attempt.statusCode >= 200 && attempt.statusCode < 300) {
    return "Delivered";
  }
  if (attempt.errorMessage || attempt.statusCode) {
    return "Failed";
  }
  return "Retrying";
}

function attemptDetail(attempt: Attempt) {
  if (attempt.statusCode) {
    return `HTTP ${attempt.statusCode}`;
  }
  return attempt.errorMessage ?? "Waiting for response";
}

function delay(milliseconds: number) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

function mergeAttempts(newAttempts: Attempt[], currentAttempts: Attempt[]) {
  const byId = new Map<string, Attempt>();
  [...newAttempts, ...currentAttempts].forEach((attempt) => byId.set(attempt.id, attempt));
  return [...byId.values()].sort((left, right) => new Date(right.attemptedAt).getTime() - new Date(left.attemptedAt).getTime());
}

function deliveryJobMessage(deliveryJobsPublished: number, duplicate: boolean) {
  if (duplicate) {
    return "Duplicate event ignored. No new delivery jobs were published.";
  }
  if (deliveryJobsPublished === 0) {
    return "Event saved, but no active subscription matched this event type.";
  }
  if (deliveryJobsPublished === 1) {
    return "Event sent. 1 delivery job published.";
  }
  return `Event sent. ${deliveryJobsPublished} delivery jobs published.`;
}

function endpointLabel(endpointId: string, endpoints: Endpoint[]) {
  return endpoints.find((endpoint) => endpoint.id === endpointId)?.url ?? endpointId;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

createRoot(document.getElementById("root")!).render(<App />);
