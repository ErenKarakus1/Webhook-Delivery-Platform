import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, Bell, Clock, Power, RefreshCcw, Server, Trash2 } from "lucide-react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const ADMIN_API_KEY = import.meta.env.VITE_ADMIN_API_KEY ?? "local-admin-key";

type Endpoint = {
  id: string;
  url: string;
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

  const stats = useMemo(() => {
    const delivered = attempts.filter((attempt) => attempt.statusCode && attempt.statusCode >= 200 && attempt.statusCode < 300);
    const successRate = attempts.length === 0 ? "0%" : `${Math.round((delivered.length / attempts.length) * 100)}%`;

    return [
      { label: "Events", value: events.length.toString(), icon: Bell },
      { label: "Success rate", value: successRate, icon: Activity },
      { label: "Pending retries", value: retries.length.toString(), icon: Clock },
      { label: "Dead-lettered", value: deadLetteredEvents.length.toString(), icon: AlertTriangle },
      { label: "Active endpoints", value: endpoints.filter((endpoint) => endpoint.active).length.toString(), icon: Server },
      { label: "Subscriptions", value: subscriptions.filter((subscription) => subscription.active).length.toString(), icon: Power },
    ];
  }, [attempts, deadLetteredEvents, endpoints, events, retries, subscriptions]);

  useEffect(() => {
    localStorage.setItem("tenantId", tenantId);
    localStorage.setItem("apiKey", apiKey);
  }, [apiKey, tenantId]);

  async function loadDashboard() {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const headers = { "X-API-Key": apiKey };
      const [endpointData, subscriptionData, eventData, attemptData, retryData, deadLetterData] = await Promise.all([
        request<Endpoint[]>(`/tenants/${tenantId}/endpoints`, headers),
        request<Subscription[]>(`/tenants/${tenantId}/subscriptions`, headers),
        request<Event[]>(`/tenants/${tenantId}/events`, headers),
        request<Attempt[]>(`/tenants/${tenantId}/attempts`, headers),
        request<Retry[]>(`/tenants/${tenantId}/retries`, headers),
        request<DeadLetteredEvent[]>(`/tenants/${tenantId}/dead-lettered-events`, headers),
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

    setEndpointLoading(true);
    setError(null);

    try {
      const endpoint = await request<Endpoint>(`/tenants/${tenantId}/endpoints`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({ url: endpointUrl.trim() }),
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

  async function deleteEndpoint(endpointId: string) {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setEndpointActionLoading(endpointId);
    setError(null);

    try {
      await requestNoContent(`/tenants/${tenantId}/endpoints/${endpointId}`, { "X-API-Key": apiKey }, { method: "DELETE" });
      setEndpoints((current) => current.filter((endpoint) => endpoint.id !== endpointId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not delete endpoint.");
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

    setSubscriptionLoading(true);
    setError(null);

    try {
      const subscription = await request<Subscription>(`/tenants/${tenantId}/subscriptions`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({
          endpointId: subscriptionEndpointId,
          eventType: subscriptionEventType.trim(),
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

  async function deleteSubscription(subscriptionId: string) {
    if (!tenantId || !apiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    setSubscriptionActionLoading(subscriptionId);
    setError(null);

    try {
      await requestNoContent(`/tenants/${tenantId}/subscriptions/${subscriptionId}`, { "X-API-Key": apiKey }, { method: "DELETE" });
      setSubscriptions((current) => current.filter((subscription) => subscription.id !== subscriptionId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not delete subscription.");
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
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not ingest event.");
    } finally {
      setEventLoading(false);
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
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not create local tenant.");
    } finally {
      setSetupLoading(false);
    }
  }

  return (
    <main className="app">
      <aside className="sidebar">
        <div className="brand">Webhook Platform</div>
        <nav>
          <a className="active" href="/">Overview</a>
          <a href="/">Endpoints</a>
          <a href="/">Events</a>
          <a href="/">Attempts</a>
          <a href="/">Settings</a>
        </nav>
      </aside>

      <section className="content">
        <header className="topbar">
          <div>
            <h1>Delivery Overview</h1>
            <p>Track webhook traffic, retries, and endpoint health.</p>
          </div>
          <button type="button" onClick={loadDashboard} disabled={loading}>
            <RefreshCcw size={16} aria-hidden="true" />
            Refresh
          </button>
        </header>

        <form className="credentials" onSubmit={submitCredentials}>
          <label>
            <span>Tenant ID</span>
            <input value={tenantId} onChange={(event) => setTenantId(event.target.value)} />
          </label>
          <label>
            <span>API key</span>
            <input type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} />
          </label>
          <button type="submit" disabled={loading}>Connect</button>
        </form>

        <div className="setup">
          <span>Need local credentials?</span>
          <button type="button" className="secondary" onClick={createTenantAndApiKey} disabled={setupLoading}>
            Create tenant
          </button>
        </div>

        {error && <div className="error">{error}</div>}

        <section className="stats">
          {stats.map(({ label, value, icon: Icon }) => (
            <article className="stat" key={label}>
              <Icon size={20} aria-hidden="true" />
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </section>

        <section className="grid">
          <DataPanel title="Recent events" empty="No events yet.">
            <form className="event-create" onSubmit={ingestEvent}>
              <div className="inline-create">
                <input
                  aria-label="Event type"
                  placeholder="order.created"
                  value={eventType}
                  onChange={(event) => setEventType(event.target.value)}
                />
                <input
                  aria-label="Idempotency key"
                  placeholder="Idempotency key"
                  value={idempotencyKey}
                  onChange={(event) => setIdempotencyKey(event.target.value)}
                />
              </div>
              <textarea
                aria-label="Event JSON payload"
                value={eventPayload}
                onChange={(event) => setEventPayload(event.target.value)}
                spellCheck={false}
              />
              <div className="form-actions">
                <button type="submit" disabled={eventLoading}>Ingest event</button>
              </div>
            </form>
            {events.slice(0, 5).map((event) => (
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
            ))}
          </DataPanel>

          <DataPanel title="Recent attempts" empty="No attempts yet.">
            {attempts.slice(0, 5).map((attempt) => (
              <article className="row attempt" key={attempt.id}>
                <div>
                  <strong>Attempt {attempt.attemptNumber}</strong>
                  <span>{attempt.eventId}</span>
                </div>
                <span className={`status ${attemptStatus(attempt).toLowerCase()}`}>{attemptStatus(attempt)}</span>
                <time>{formatDate(attempt.attemptedAt)}</time>
              </article>
            ))}
          </DataPanel>

          <DataPanel title="Endpoints" empty="No endpoints configured.">
            <form className="inline-create" onSubmit={createEndpoint}>
              <input
                aria-label="Endpoint HTTPS URL"
                placeholder="https://example.com/webhooks"
                value={endpointUrl}
                onChange={(event) => setEndpointUrl(event.target.value)}
              />
              <button type="submit" disabled={endpointLoading}>Add</button>
            </form>
            {endpoints.slice(0, 5).map((endpoint) => (
              <article className="row endpoint-row" key={endpoint.id}>
                <div>
                  <strong>{endpoint.active ? "Active" : "Inactive"}</strong>
                  <span>{endpoint.url}</span>
                </div>
                <div className="row-actions">
                  <button
                    aria-label={endpoint.active ? "Deactivate endpoint" : "Activate endpoint"}
                    className="icon-button"
                    disabled={endpointActionLoading === endpoint.id}
                    onClick={() => void setEndpointActive(endpoint, !endpoint.active)}
                    type="button"
                  >
                    <Power size={16} aria-hidden="true" />
                  </button>
                  <button
                    aria-label="Delete endpoint"
                    className="icon-button danger"
                    disabled={endpointActionLoading === endpoint.id}
                    onClick={() => void deleteEndpoint(endpoint.id)}
                    type="button"
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                </div>
                <time>{formatDate(endpoint.createdAt)}</time>
              </article>
            ))}
          </DataPanel>

          <DataPanel title="Subscriptions" empty="No subscriptions configured.">
            <form className="inline-create subscription-create" onSubmit={createSubscription}>
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
                placeholder="order.created"
                value={subscriptionEventType}
                onChange={(event) => setSubscriptionEventType(event.target.value)}
              />
              <button type="submit" disabled={subscriptionLoading}>Add</button>
            </form>
            {subscriptions.slice(0, 5).map((subscription) => (
              <article className="row endpoint-row" key={subscription.id}>
                <div>
                  <strong>{subscription.active ? "Active" : "Inactive"} · {subscription.eventType}</strong>
                  <span>{endpointLabel(subscription.endpointId, endpoints)}</span>
                </div>
                <div className="row-actions">
                  <button
                    aria-label={subscription.active ? "Deactivate subscription" : "Activate subscription"}
                    className="icon-button"
                    disabled={subscriptionActionLoading === subscription.id}
                    onClick={() => void setSubscriptionActive(subscription, !subscription.active)}
                    type="button"
                  >
                    <Power size={16} aria-hidden="true" />
                  </button>
                  <button
                    aria-label="Delete subscription"
                    className="icon-button danger"
                    disabled={subscriptionActionLoading === subscription.id}
                    onClick={() => void deleteSubscription(subscription.id)}
                    type="button"
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                </div>
                <time>{formatDate(subscription.createdAt)}</time>
              </article>
            ))}
          </DataPanel>

          <DataPanel title="Retry queue" empty="No pending retries.">
            {retries.slice(0, 5).map((retry) => (
              <article className="row" key={retry.id}>
                <div>
                  <strong>Attempt {retry.attemptNumber}</strong>
                  <span>{retry.eventId}</span>
                </div>
                <time>{formatDate(retry.dueAt)}</time>
              </article>
            ))}
          </DataPanel>

          <DataPanel title="Dead-lettered events" empty="No dead-lettered events.">
            {deadLetteredEvents.slice(0, 5).map((event) => (
              <article className="row attempt" key={event.id}>
                <div>
                  <strong>{event.eventType}</strong>
                  <span>{event.errorMessage ?? event.eventId}</span>
                </div>
                <span className="status failed">{event.statusCode ?? "Failed"}</span>
                <time>{formatDate(event.createdAt)}</time>
              </article>
            ))}
          </DataPanel>

          <section className="panel event-detail">
            <div className="panel-header">
              <h2>Event detail</h2>
              {detailLoading && <span>Loading</span>}
            </div>
            {selectedEvent ? (
              <div className="detail-body">
                <div className="detail-summary">
                  <span>Event type</span>
                  <strong>{selectedEvent.eventType}</strong>
                  <span>Event ID</span>
                  <code>{selectedEvent.id}</code>
                  <span>Created</span>
                  <time>{formatDate(selectedEvent.createdAt)}</time>
                </div>
                <div className="timeline">
                  {selectedEventAttempts.length > 0 ? selectedEventAttempts.map((attempt) => (
                    <article className="timeline-item" key={attempt.id}>
                      <span className={`status ${attemptStatus(attempt).toLowerCase()}`}>{attemptStatus(attempt)}</span>
                      <div>
                        <strong>Attempt {attempt.attemptNumber}</strong>
                        <span>{attempt.statusCode ? `HTTP ${attempt.statusCode}` : attempt.errorMessage ?? "No response yet"}</span>
                      </div>
                      <time>{formatDate(attempt.attemptedAt)}</time>
                    </article>
                  )) : <p className="empty">No delivery attempts for this event yet.</p>}
                </div>
              </div>
            ) : (
              <p className="empty">Select an event to inspect delivery attempts.</p>
            )}
          </section>
        </section>
      </section>
    </main>
  );
}

function DataPanel({ children, empty, title }: { children: React.ReactNode; empty: string; title: string }) {
  const items = React.Children.toArray(children).filter(Boolean);
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>{title}</h2>
      </div>
      <div className="list">
        {items.length > 0 ? items : <p className="empty">{empty}</p>}
      </div>
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

async function requestNoContent(path: string, headers: Record<string, string>, init: RequestInit & { headers?: Record<string, string> } = {}): Promise<void> {
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
