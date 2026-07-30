import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { Activity, AlertTriangle, Bell, Clock, Globe, LoaderCircle, RefreshCcw, Route } from "lucide-react";
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
  payload?: unknown;
  idempotencyKey?: string | null;
  createdAt: string;
};

type Attempt = {
  id: string;
  eventId: string;
  endpointId: string;
  attemptNumber: number;
  statusCode: number | null;
  responseBody: string | null;
  errorMessage: string | null;
  attemptedAt: string;
};

type AttemptStatusFilter = "all" | "delivered" | "failed";

type Retry = {
  id: string;
  eventId: string;
  endpointId: string;
  attemptNumber: number;
  dueAt: string;
};

type LoadDashboardOptions = {
  showLoading?: boolean;
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

class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

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
  const [selectedEndpoint, setSelectedEndpoint] = useState<Endpoint | null>(null);
  const [selectedEventAttempts, setSelectedEventAttempts] = useState<Attempt[]>([]);
  const [eventTypeFilter, setEventTypeFilter] = useState("");
  const [attemptStatusFilter, setAttemptStatusFilter] = useState<AttemptStatusFilter>("all");
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
  const [retryActionLoading, setRetryActionLoading] = useState<string | null>(null);
  const [deadLetterActionLoading, setDeadLetterActionLoading] = useState<string | null>(null);
  const [eventLoading, setEventLoading] = useState(false);
  const [setupLoading, setSetupLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [endpointError, setEndpointError] = useState<string | null>(null);
  const [subscriptionError, setSubscriptionError] = useState<string | null>(null);
  const [retryError, setRetryError] = useState<string | null>(null);
  const [deadLetterError, setDeadLetterError] = useState<string | null>(null);
  const [sendResult, setSendResult] = useState<string | null>(null);

  const stats = useMemo(() => {
    const delivered = attempts.filter((attempt) => attempt.statusCode && attempt.statusCode >= 200 && attempt.statusCode < 300);
    const successRate = attempts.length === 0 ? "0%" : `${Math.round((delivered.length / attempts.length) * 100)}%`;

    return [
      { label: "Events", value: events.length.toString(), icon: Bell },
      { label: "Success rate", value: successRate, icon: Activity },
      { label: "Attempts", value: attempts.length.toString(), icon: Route },
      { label: "Endpoints", value: endpoints.filter((endpoint) => endpoint.active).length.toString(), icon: Globe },
      { label: "Subscriptions", value: subscriptions.filter((subscription) => subscription.active).length.toString(), icon: Activity },
      { label: "Retries", value: retries.length.toString(), icon: Clock },
      { label: "Dead letters", value: deadLetteredEvents.length.toString(), icon: AlertTriangle },
    ];
  }, [attempts, deadLetteredEvents, endpoints, events, retries, subscriptions]);

  const filteredEvents = useMemo(() => {
    const normalizedFilter = eventTypeFilter.trim().toLowerCase();
    if (!normalizedFilter) {
      return events;
    }
    return events.filter((event) => event.eventType.toLowerCase().includes(normalizedFilter));
  }, [eventTypeFilter, events]);
  const visibleEvents = filteredEvents;

  const baseAttempts = selectedEndpoint
    ? attempts.filter((attempt) => attempt.endpointId === selectedEndpoint.id)
    : selectedEvent ? selectedEventAttempts : attempts;
  const filteredAttempts = useMemo(() => (
    baseAttempts.filter((attempt) => attemptStatusFilter === "all" || attemptStatus(attempt).toLowerCase() === attemptStatusFilter)
  ), [attemptStatusFilter, baseAttempts]);
  const recentAttempts = selectedEvent || selectedEndpoint ? filteredAttempts : filteredAttempts.slice(0, 6);
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

  useEffect(() => {
    const savedTenantId = localStorage.getItem("tenantId");
    const savedApiKey = localStorage.getItem("apiKey");
    if (savedTenantId && savedApiKey) {
      void loadDashboard(savedTenantId, savedApiKey);
    }
  }, []);

  useEffect(() => {
    if (!tenantId || !apiKey) {
      return undefined;
    }

    const intervalId = window.setInterval(() => {
      void loadDashboard(tenantId, apiKey, { showLoading: false });
    }, 5000);

    return () => window.clearInterval(intervalId);
  }, [apiKey, selectedEndpoint, tenantId]);

  async function loadDashboard(nextTenantId = tenantId, nextApiKey = apiKey, options: LoadDashboardOptions = {}) {
    if (!nextTenantId || !nextApiKey) {
      setError("Tenant ID and API key are required.");
      return;
    }

    const showLoading = options.showLoading ?? true;
    if (showLoading) {
      setLoading(true);
    }
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
      setSelectedEvent((current) => {
        if (selectedEndpoint) {
          setSelectedEventAttempts([]);
          return null;
        }
        const nextSelectedEvent = current
          ? eventData.find((event) => event.id === current.id) ?? eventData[0] ?? null
          : eventData[0] ?? null;
        setSelectedEventAttempts(nextSelectedEvent ? attemptData.filter((attempt) => attempt.eventId === nextSelectedEvent.id) : []);
        return nextSelectedEvent;
      });
      setSelectedEndpoint((current) => (
        current ? endpointData.find((endpoint) => endpoint.id === current.id) ?? null : null
      ));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load dashboard data.");
    } finally {
      if (showLoading) {
        setLoading(false);
      }
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
      setSelectedEndpoint(null);
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

  function selectEndpoint(endpoint: Endpoint) {
    setSelectedEndpoint(endpoint);
    setSelectedEvent(null);
    setSelectedEventAttempts([]);
  }

  function clearEndpointSelection() {
    setSelectedEndpoint(null);
    setSelectedEvent((current) => current ?? events[0] ?? null);
    setSelectedEventAttempts(events[0] ? attempts.filter((attempt) => attempt.eventId === events[0].id) : []);
  }

  async function createEndpoint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setEndpointError("Tenant ID and API key are required.");
      return;
    }
    if (!endpointUrl.trim()) {
      setEndpointError("Endpoint URL is required.");
      return;
    }

    const normalizedUrl = endpointUrl.trim();
    const urlError = validateEndpointUrl(normalizedUrl);
    if (urlError) {
      setEndpointError(urlError);
      return;
    }

    if (endpoints.some((endpoint) => endpoint.url.toLowerCase() === normalizedUrl.toLowerCase())) {
      setEndpointUrl("");
      setEndpointError("That endpoint already exists.");
      return;
    }

    setEndpointLoading(true);
    setError(null);
    setEndpointError(null);

    try {
      const endpoint = await request<Endpoint>(`/tenants/${tenantId}/endpoints`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({ url: normalizedUrl }),
      });
      setEndpoints((current) => [endpoint, ...current]);
      setSubscriptionEndpointId((current) => current || endpoint.id);
      setEndpointUrl("");
      await loadDashboard();
    } catch (exception) {
      setEndpointError(endpointApiErrorMessage(exception));
    } finally {
      setEndpointLoading(false);
    }
  }

  async function setEndpointActive(endpoint: Endpoint, active: boolean) {
    if (!tenantId || !apiKey) {
      setEndpointError("Tenant ID and API key are required.");
      return;
    }

    setEndpointActionLoading(endpoint.id);
    setEndpointError(null);

    try {
      const updatedEndpoint = await request<Endpoint>(
        `/tenants/${tenantId}/endpoints/${endpoint.id}/${active ? "activate" : "deactivate"}`,
        { "X-API-Key": apiKey },
        { method: "PATCH" },
      );
      setEndpoints((current) => current.map((item) => item.id === updatedEndpoint.id ? updatedEndpoint : item));
      await loadDashboard();
    } catch (exception) {
      setEndpointError(exception instanceof Error ? exception.message : "Could not update endpoint.");
    } finally {
      setEndpointActionLoading(null);
    }
  }

  async function deleteEndpoint(endpoint: Endpoint) {
    if (!tenantId || !apiKey) {
      setEndpointError("Tenant ID and API key are required.");
      return;
    }

    setEndpointActionLoading(endpoint.id);
    setEndpointError(null);

    try {
      await request<void>(
        `/tenants/${tenantId}/endpoints/${endpoint.id}`,
        { "X-API-Key": apiKey },
        { method: "DELETE" },
      );
      setEndpoints((current) => current.filter((item) => item.id !== endpoint.id));
      setSubscriptions((current) => current.filter((item) => item.endpointId !== endpoint.id));
      setSubscriptionEndpointId((current) => current === endpoint.id ? "" : current);
      await loadDashboard();
    } catch (exception) {
      setEndpointError(exception instanceof Error ? exception.message : "Could not delete endpoint.");
    } finally {
      setEndpointActionLoading(null);
    }
  }

  async function createSubscription(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setSubscriptionError("Tenant ID and API key are required.");
      return;
    }
    if (!subscriptionEndpointId) {
      setSubscriptionError("Select an endpoint for the subscription.");
      return;
    }
    if (!subscriptionEventType.trim()) {
      setSubscriptionError("Subscription event type is required.");
      return;
    }

    const normalizedEventType = subscriptionEventType.trim();
    if (subscriptions.some((subscription) => (
      subscription.endpointId === subscriptionEndpointId
      && subscription.eventType.toLowerCase() === normalizedEventType.toLowerCase()
    ))) {
      setSubscriptionError("That endpoint already has a subscription for this event type.");
      return;
    }

    setSubscriptionLoading(true);
    setSubscriptionError(null);

    try {
      const subscription = await request<Subscription>(`/tenants/${tenantId}/subscriptions`, { "X-API-Key": apiKey }, {
        method: "POST",
        body: JSON.stringify({
          endpointId: subscriptionEndpointId,
          eventType: normalizedEventType,
        }),
      });
      setSubscriptions((current) => [subscription, ...current]);
      await loadDashboard();
    } catch (exception) {
      setSubscriptionError(exception instanceof Error ? exception.message : "Could not create subscription.");
    } finally {
      setSubscriptionLoading(false);
    }
  }

  async function setSubscriptionActive(subscription: Subscription, active: boolean) {
    if (!tenantId || !apiKey) {
      setSubscriptionError("Tenant ID and API key are required.");
      return;
    }

    setSubscriptionActionLoading(subscription.id);
    setSubscriptionError(null);

    try {
      const updatedSubscription = await request<Subscription>(
        `/tenants/${tenantId}/subscriptions/${subscription.id}/${active ? "activate" : "deactivate"}`,
        { "X-API-Key": apiKey },
        { method: "PATCH" },
      );
      setSubscriptions((current) => current.map((item) => item.id === updatedSubscription.id ? updatedSubscription : item));
      await loadDashboard();
    } catch (exception) {
      setSubscriptionError(exception instanceof Error ? exception.message : "Could not update subscription.");
    } finally {
      setSubscriptionActionLoading(null);
    }
  }

  async function deleteSubscription(subscription: Subscription) {
    if (!tenantId || !apiKey) {
      setSubscriptionError("Tenant ID and API key are required.");
      return;
    }

    setSubscriptionActionLoading(subscription.id);
    setSubscriptionError(null);

    try {
      await request<void>(
        `/tenants/${tenantId}/subscriptions/${subscription.id}`,
        { "X-API-Key": apiKey },
        { method: "DELETE" },
      );
      setSubscriptions((current) => current.filter((item) => item.id !== subscription.id));
      await loadDashboard();
    } catch (exception) {
      setSubscriptionError(exception instanceof Error ? exception.message : "Could not delete subscription.");
    } finally {
      setSubscriptionActionLoading(null);
    }
  }

  async function dispatchRetry(retry: Retry) {
    if (!tenantId || !apiKey) {
      setRetryError("Tenant ID and API key are required.");
      return;
    }

    setRetryActionLoading(retry.id);
    setRetryError(null);

    try {
      await request<Retry>(
        `/tenants/${tenantId}/retries/${retry.id}/dispatch`,
        { "X-API-Key": apiKey },
        { method: "POST" },
      );
      setRetries((current) => current.filter((item) => item.id !== retry.id));
      await loadDashboard();
    } catch (exception) {
      setRetryError(exception instanceof Error ? exception.message : "Could not dispatch retry.");
    } finally {
      setRetryActionLoading(null);
    }
  }

  async function deleteRetry(retry: Retry) {
    if (!tenantId || !apiKey) {
      setRetryError("Tenant ID and API key are required.");
      return;
    }

    setRetryActionLoading(retry.id);
    setRetryError(null);

    try {
      await request<void>(
        `/tenants/${tenantId}/retries/${retry.id}`,
        { "X-API-Key": apiKey },
        { method: "DELETE" },
      );
      setRetries((current) => current.filter((item) => item.id !== retry.id));
      await loadDashboard();
    } catch (exception) {
      setRetryError(exception instanceof Error ? exception.message : "Could not cancel retry.");
    } finally {
      setRetryActionLoading(null);
    }
  }

  async function deleteDeadLetteredEvent(event: DeadLetteredEvent) {
    if (!tenantId || !apiKey) {
      setDeadLetterError("Tenant ID and API key are required.");
      return;
    }

    setDeadLetterActionLoading(event.id);
    setDeadLetterError(null);

    try {
      await request<void>(
        `/tenants/${tenantId}/dead-lettered-events/${event.id}`,
        { "X-API-Key": apiKey },
        { method: "DELETE" },
      );
      setDeadLetteredEvents((current) => current.filter((item) => item.id !== event.id));
      await loadDashboard();
    } catch (exception) {
      setDeadLetterError(exception instanceof Error ? exception.message : "Could not clear dead-lettered event.");
    } finally {
      setDeadLetterActionLoading(null);
    }
  }

  async function replayDeadLetteredEvent(event: DeadLetteredEvent) {
    if (!tenantId || !apiKey) {
      setDeadLetterError("Tenant ID and API key are required.");
      return;
    }

    setDeadLetterActionLoading(event.id);
    setDeadLetterError(null);

    try {
      await request<DeadLetteredEvent>(
        `/tenants/${tenantId}/dead-lettered-events/${event.id}/replay`,
        { "X-API-Key": apiKey },
        { method: "POST" },
      );
      setDeadLetteredEvents((current) => current.filter((item) => item.id !== event.id));
      await loadDashboard();
    } catch (exception) {
      setDeadLetterError(exception instanceof Error ? exception.message : "Could not replay dead-lettered event.");
    } finally {
      setDeadLetterActionLoading(null);
    }
  }

  async function ingestEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!tenantId || !apiKey) {
      setSendError("Tenant ID and API key are required.");
      return;
    }
    if (!eventType.trim()) {
      setSendError("Event type is required.");
      return;
    }

    let payload: unknown;
    try {
      payload = JSON.parse(eventPayload);
    } catch {
      setSendError("Event payload must be valid JSON.");
      return;
    }

    setEventLoading(true);
    setSendError(null);
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
        payload,
        idempotencyKey: idempotencyKey.trim() || null,
        createdAt: response.createdAt,
      };
      setEvents((current) => [createdEvent, ...current.filter((item) => item.id !== createdEvent.id)]);
      setSelectedEvent(createdEvent);
      setSelectedEndpoint(null);
      setSelectedEventAttempts([]);
      setIdempotencyKey("");
      setSendResult(deliveryJobMessage(response.deliveryJobsPublished, response.duplicate));
      await waitForEventAttempts(createdEvent.id);
      await loadDashboard();
    } catch (exception) {
      setSendError(exception instanceof Error ? exception.message : "Could not ingest event.");
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
          <h1>Webhook operations dashboard</h1>
        </div>
        <button type="button" onClick={() => void loadDashboard()} disabled={loading}>
          {loading ? <LoaderCircle className="spin" size={16} aria-hidden="true" /> : <RefreshCcw size={16} aria-hidden="true" />}
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
        <button type="submit" disabled={loading}>
          {loading && <LoaderCircle className="spin" size={16} aria-hidden="true" />}
          Load dashboard
        </button>
        <button type="button" className="secondary" onClick={createTenantAndApiKey} disabled={setupLoading}>
          {setupLoading && <LoaderCircle className="spin" size={16} aria-hidden="true" />}
          New tenant
        </button>
      </form>

      {error && (
        <div className="error" role="alert">
          <span>{error}</span>
        </div>
      )}

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
        <section className="panel list-panel send-panel">
          <PanelHeader title="Send event" meta="Publish test event" />
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
              <span>JSON payload</span>
              <textarea value={eventPayload} onChange={(event) => setEventPayload(event.target.value)} spellCheck={false} />
            </label>
            <button type="submit" disabled={eventLoading}>
              {eventLoading && <LoaderCircle className="spin" size={16} aria-hidden="true" />}
              Send event
            </button>
            {sendError && <p className="form-error" role="alert">{sendError}</p>}
            {sendResult && <p className="result-message">{sendResult}</p>}
          </form>
        </section>

        <section className="panel list-panel">
          <PanelHeader title="Events" meta={`${events.length} total`} />
          {selectedEndpoint && (
            <div className="panel-action-bar">
              <button className="secondary compact-action" onClick={clearEndpointSelection} type="button">
                Back to events
              </button>
            </div>
          )}
          <div className="panel-controls">
            <input
              aria-label="Filter events by type"
              placeholder="Filter event type"
              value={eventTypeFilter}
              onChange={(event) => setEventTypeFilter(event.target.value)}
            />
          </div>
          <div className="list scroll-list events-scroll">
            {visibleEvents.length > 0 ? visibleEvents.map((event) => (
              <button
                className={`row row-button ${selectedEvent?.id === event.id ? "selected" : ""}`}
                disabled={selectedEndpoint !== null}
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
            )) : <p className="empty">{events.length > 0 ? "No events match this filter." : "No events yet."}</p>}
          </div>
        </section>

        <section className="panel list-panel">
          <PanelHeader title={deliveryPanelTitle(selectedEvent, selectedEndpoint)} meta={detailLoading ? "Loading" : `${recentAttempts.length} shown`} />
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
          {selectedEvent?.payload !== undefined && (
            <details className="payload-detail" open>
              <summary>Payload</summary>
              <pre>
                <code>{formatJson(selectedEvent.payload)}</code>
              </pre>
            </details>
          )}
          {selectedEndpoint && (
            <div className="selected-event">
              <div>
                <span>Endpoint</span>
                <strong>{selectedEndpoint.active ? "Active" : "Inactive"}</strong>
              </div>
              <div>
                <span>Endpoint ID</span>
                <code>{selectedEndpoint.id}</code>
              </div>
              <div>
                <span>Recent attempts</span>
                <strong>{recentAttempts.length}</strong>
              </div>
              <div>
                <span>URL</span>
                <strong>{selectedEndpoint.url}</strong>
              </div>
            </div>
          )}
          <div className="panel-controls">
            <select
              aria-label="Filter attempts by status"
              value={attemptStatusFilter}
              onChange={(event) => setAttemptStatusFilter(event.target.value as AttemptStatusFilter)}
            >
              <option value="all">All attempts</option>
              <option value="delivered">Delivered</option>
              <option value="failed">Failed</option>
            </select>
          </div>
          <div className="list scroll-list attempts-scroll">
            {recentAttempts.length > 0 ? recentAttempts.map((attempt) => (
              <article className="row attempt" key={attempt.id}>
                <div>
                  <strong>{attemptStatus(attempt)} to {endpointLabel(attempt.endpointId, endpoints)}</strong>
                  <small>Attempt {attempt.attemptNumber}</small>
                  <span>{attemptDetail(attempt)} / endpoint {shortId(attempt.endpointId)}</span>
                  {attemptExtraDetail(attempt) && <span>{attemptExtraDetail(attempt)}</span>}
                </div>
                <span className={`status ${attemptStatus(attempt).toLowerCase()}`}>{attemptStatus(attempt)}</span>
                <time>{formatDate(attempt.attemptedAt)}</time>
              </article>
            )) : (
              <p className="empty">
                {deliveryEmptyMessage(selectedEvent, selectedEndpoint)}
              </p>
            )}
          </div>
        </section>

        <section className="panel setup-panel">
          <PanelHeader title="Configuration" meta={`${endpoints.length} endpoints, ${subscriptions.length} subscriptions`} />
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
                <button type="submit" disabled={endpointLoading}>
                  {endpointLoading && <LoaderCircle className="spin" size={16} aria-hidden="true" />}
                  Add endpoint
                </button>
              </form>
              {endpointError && <p className="form-error" role="alert">{endpointError}</p>}
              <SimpleList empty="No endpoints configured.">
                {endpoints.slice(0, 4).map((endpoint) => (
                  <article className={`compact-row ${selectedEndpoint?.id === endpoint.id ? "selected-row" : ""}`} key={endpoint.id}>
                    <div>
                      <button className="row-button endpoint-select" onClick={() => selectEndpoint(endpoint)} type="button">
                        <strong>{endpoint.active ? "Active" : "Inactive"}</strong>
                        <small>{endpoint.active ? "Receives matching events" : "Kept for history; no new deliveries"}</small>
                        <span>{endpoint.url}</span>
                      </button>
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
                    <div className="row-actions">
                      <button
                        aria-label={endpoint.active ? "Deactivate endpoint" : "Activate endpoint"}
                        className="secondary compact-action"
                        disabled={endpointActionLoading === endpoint.id}
                        onClick={() => void setEndpointActive(endpoint, !endpoint.active)}
                        type="button"
                      >
                        {endpoint.active ? "Deactivate" : "Activate"}
                      </button>
                      <button
                        aria-label="Delete endpoint"
                        className="danger compact-action"
                        disabled={endpointActionLoading === endpoint.id}
                        onClick={() => void deleteEndpoint(endpoint)}
                        type="button"
                      >
                        Delete
                      </button>
                    </div>
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
                <button type="submit" disabled={subscriptionLoading}>
                  {subscriptionLoading && <LoaderCircle className="spin" size={16} aria-hidden="true" />}
                  Add subscription
                </button>
              </form>
              {subscriptionError && <p className="form-error" role="alert">{subscriptionError}</p>}
              <SimpleList empty="No subscriptions configured.">
                {subscriptions.slice(0, 4).map((subscription) => (
                  <article className="compact-row" key={subscription.id}>
                    <div>
                      <strong>{subscription.active ? "Active" : "Inactive"} / {subscription.eventType}</strong>
                      <small>{subscription.active ? "Publishes delivery jobs" : "Kept for history; no new jobs"}</small>
                      <span>{endpointLabel(subscription.endpointId, endpoints)}</span>
                    </div>
                    <div className="row-actions">
                      <button
                        aria-label={subscription.active ? "Deactivate subscription" : "Activate subscription"}
                        className="secondary compact-action"
                        disabled={subscriptionActionLoading === subscription.id}
                        onClick={() => void setSubscriptionActive(subscription, !subscription.active)}
                        type="button"
                      >
                        {subscription.active ? "Deactivate" : "Activate"}
                      </button>
                      <button
                        aria-label="Delete subscription"
                        className="danger compact-action"
                        disabled={subscriptionActionLoading === subscription.id}
                        onClick={() => void deleteSubscription(subscription)}
                        type="button"
                      >
                        Delete
                      </button>
                    </div>
                  </article>
                ))}
              </SimpleList>
            </div>
          </div>
        </section>
      </section>

      <details className="advanced">
        <summary>Retry and dead-letter queues</summary>
        <div className="advanced-grid">
          <Queue title="Retry queue" empty="No pending retries.">
            {retryError && <p className="form-error queue-error" role="alert">{retryError}</p>}
            {retries.map((retry) => (
              <article className="compact-row" key={retry.id}>
                <div>
                  <strong>Attempt {retry.attemptNumber}</strong>
                  <small>{endpointLabel(retry.endpointId, endpoints)}</small>
                  <span>{retryDueLabel(retry.dueAt)} / Event {shortId(retry.eventId)} / Endpoint {shortId(retry.endpointId)}</span>
                </div>
                <div className="row-actions">
                  <time>{formatDate(retry.dueAt)}</time>
                  <button
                    className="secondary compact-action"
                    disabled={retryActionLoading === retry.id}
                    onClick={() => void dispatchRetry(retry)}
                    type="button"
                  >
                    Retry now
                  </button>
                  <button
                    className="danger compact-action"
                    disabled={retryActionLoading === retry.id}
                    onClick={() => void deleteRetry(retry)}
                    type="button"
                  >
                    Cancel
                  </button>
                </div>
              </article>
            ))}
          </Queue>
          <Queue title="Dead letters" empty="No dead-lettered events.">
            {deadLetterError && <p className="form-error queue-error" role="alert">{deadLetterError}</p>}
            {deadLetteredEvents.map((event) => (
              <article className="compact-row" key={event.id}>
                <div>
                  <strong>{event.eventType}</strong>
                  <small>{endpointLabel(event.endpointId, endpoints)}</small>
                  <span>{event.errorMessage ?? event.eventId}</span>
                </div>
                <div className="row-actions">
                  <span className="status failed">{event.statusCode ?? "Failed"}</span>
                  <button
                    className="secondary compact-action"
                    disabled={deadLetterActionLoading === event.id}
                    onClick={() => void replayDeadLetteredEvent(event)}
                    type="button"
                  >
                    Replay
                  </button>
                  <button
                    className="secondary compact-action"
                    disabled={deadLetterActionLoading === event.id}
                    onClick={() => void deleteDeadLetteredEvent(event)}
                    type="button"
                  >
                    Clear
                  </button>
                </div>
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

function deliveryPanelTitle(selectedEvent: Event | null, selectedEndpoint: Endpoint | null) {
  if (selectedEndpoint) {
    return "Endpoint delivery history";
  }
  return selectedEvent ? "Selected delivery" : "Delivery attempts";
}

function deliveryEmptyMessage(selectedEvent: Event | null, selectedEndpoint: Endpoint | null) {
  if (selectedEndpoint) {
    return "No delivery attempts for this endpoint yet.";
  }
  return selectedEvent ? "No matching active subscription for this event type, or delivery has not started yet." : "No delivery attempts yet.";
}

function SimpleList({ children, empty }: { children: React.ReactNode; empty: string }) {
  const items = React.Children.toArray(children).filter(Boolean);
  return <div className="simple-list">{items.length > 0 ? items : <p className="empty">{empty}</p>}</div>;
}

function Queue({ children, empty, title }: { children: React.ReactNode; empty: string; title: string }) {
  return (
    <section className="queue">
      <h2>{title}</h2>
      <div className="queue-scroll">
        <SimpleList empty={empty}>{children}</SimpleList>
      </div>
    </section>
  );
}

async function request<T>(path: string, headers: Record<string, string>, init: RequestInit & { headers?: Record<string, string> } = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...headers,
        ...init.headers,
      },
    });
  } catch (exception) {
    throw new ApiRequestError(networkErrorMessage(exception), 0);
  }
  if (!response.ok) {
    throw await readApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.includes("application/json")) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

function networkErrorMessage(exception: unknown) {
  if (exception instanceof TypeError && exception.message.toLowerCase().includes("failed to fetch")) {
    return "The API did not respond. Try again after the services finish starting.";
  }
  return exception instanceof Error ? exception.message : "Could not reach the API.";
}

async function readApiError(response: Response) {
  const fallback = `${response.status} ${response.statusText}`;
  const contentType = response.headers.get("Content-Type") ?? "";

  if (contentType.includes("application/json")) {
    try {
      const body = await response.json() as Partial<{ code: string; error: string; message: string; path: string; status: number }>;
      const message = friendlyApiMessage(response.status, body.code, body.message || body.error || fallback);
      return new ApiRequestError(message, response.status, body.code);
    } catch {
      return new ApiRequestError(fallback, response.status);
    }
  }

  try {
    const text = await response.text();
    return new ApiRequestError(text.trim() || fallback, response.status);
  } catch {
    return new ApiRequestError(fallback, response.status);
  }
}

function friendlyApiMessage(status: number, code: string | undefined, message: string) {
  if (status === 429 || code === "rate_limit_exceeded") {
    return message || "Rate limit exceeded. Wait a moment and try again.";
  }
  if (status === 400 && isBareStatusMessage(message)) {
    return "Request is invalid. Check the fields and try again.";
  }
  return message;
}

function isBareStatusMessage(message: string) {
  return /^(400|400 bad request|bad request)$/i.test(message.trim());
}

function validateEndpointUrl(value: string) {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return "Enter a valid HTTPS URL.";
  }
  if (url.protocol !== "https:") {
    return "Endpoint URL must start with https://.";
  }
  if (!url.hostname) {
    return "Endpoint URL must include a host.";
  }
  if (url.username || url.password) {
    return "Endpoint URL must not include username or password.";
  }
  return null;
}

function endpointApiErrorMessage(exception: unknown) {
  if (!(exception instanceof ApiRequestError)) {
    return exception instanceof Error ? exception.message : "Could not create endpoint.";
  }
  if (exception.status === 400 && isBareStatusMessage(exception.message)) {
    return "Endpoint host could not be verified. Use a real, reachable HTTPS domain.";
  }
  return exception.message;
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

function attemptExtraDetail(attempt: Attempt) {
  if (attempt.errorMessage) {
    return attempt.errorMessage;
  }
  if (attempt.responseBody) {
    return `Response: ${attempt.responseBody}`;
  }
  return null;
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

function shortId(value: string) {
  return value.slice(0, 8);
}

function retryDueLabel(value: string) {
  const milliseconds = new Date(value).getTime() - Date.now();
  if (milliseconds <= 0) {
    return "Retry due now";
  }

  const seconds = Math.ceil(milliseconds / 1000);
  if (seconds < 60) {
    return `Retries in ${seconds}s`;
  }

  const minutes = Math.ceil(seconds / 60);
  if (minutes < 60) {
    return `Retries in ${minutes}m`;
  }

  const hours = Math.ceil(minutes / 60);
  return `Retries in ${hours}h`;
}

function formatJson(value: unknown) {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
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
