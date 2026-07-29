import React, { FormEvent, useEffect, useMemo, useState } from "react";
import { Activity, Bell, Clock, RefreshCcw, Server } from "lucide-react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

type Endpoint = {
  id: string;
  url: string;
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

function App() {
  const [tenantId, setTenantId] = useState(() => localStorage.getItem("tenantId") ?? "");
  const [apiKey, setApiKey] = useState(() => localStorage.getItem("apiKey") ?? "");
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [retries, setRetries] = useState<Retry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stats = useMemo(() => {
    const delivered = attempts.filter((attempt) => attempt.statusCode && attempt.statusCode >= 200 && attempt.statusCode < 300);
    const successRate = attempts.length === 0 ? "0%" : `${Math.round((delivered.length / attempts.length) * 100)}%`;

    return [
      { label: "Events", value: events.length.toString(), icon: Bell },
      { label: "Success rate", value: successRate, icon: Activity },
      { label: "Pending retries", value: retries.length.toString(), icon: Clock },
      { label: "Active endpoints", value: endpoints.filter((endpoint) => endpoint.active).length.toString(), icon: Server },
    ];
  }, [attempts, endpoints, events, retries]);

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
      const [endpointData, eventData, attemptData, retryData] = await Promise.all([
        request<Endpoint[]>(`/tenants/${tenantId}/endpoints`, headers),
        request<Event[]>(`/tenants/${tenantId}/events`, headers),
        request<Attempt[]>(`/tenants/${tenantId}/attempts`, headers),
        request<Retry[]>(`/tenants/${tenantId}/retries`, headers),
      ]);

      setEndpoints(endpointData);
      setEvents(eventData);
      setAttempts(attemptData);
      setRetries(retryData);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Could not load dashboard data.");
    } finally {
      setLoading(false);
    }
  }

  function submitCredentials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadDashboard();
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
            {events.slice(0, 5).map((event) => (
              <article className="row" key={event.id}>
                <div>
                  <strong>{event.eventType}</strong>
                  <span>{event.id}</span>
                </div>
                <time>{formatDate(event.createdAt)}</time>
              </article>
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
            {endpoints.slice(0, 5).map((endpoint) => (
              <article className="row" key={endpoint.id}>
                <div>
                  <strong>{endpoint.active ? "Active" : "Inactive"}</strong>
                  <span>{endpoint.url}</span>
                </div>
                <time>{formatDate(endpoint.createdAt)}</time>
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

async function request<T>(path: string, headers: Record<string, string>): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, { headers });
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric",
  }).format(new Date(value));
}

createRoot(document.getElementById("root")!).render(<App />);
