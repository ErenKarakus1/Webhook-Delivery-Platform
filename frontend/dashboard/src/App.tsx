import React from "react";
import { Activity, Bell, Clock, Server } from "lucide-react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const stats = [
  { label: "Events today", value: "12,480", icon: Bell },
  { label: "Success rate", value: "99.4%", icon: Activity },
  { label: "Pending retries", value: "184", icon: Clock },
  { label: "Active endpoints", value: "326", icon: Server },
];

const attempts = [
  { endpoint: "https://api.acme.test/webhooks", event: "invoice.paid", status: "Delivered", time: "14s ago" },
  { endpoint: "https://ops.example.test/hooks", event: "user.created", status: "Retrying", time: "2m ago" },
  { endpoint: "https://billing.test/events", event: "payment.failed", status: "Failed", time: "8m ago" },
];

function App() {
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
          <button type="button">New endpoint</button>
        </header>

        <section className="stats">
          {stats.map(({ label, value, icon: Icon }) => (
            <article className="stat" key={label}>
              <Icon size={20} aria-hidden="true" />
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </section>

        <section className="panel">
          <div className="panel-header">
            <h2>Recent attempts</h2>
            <span>Live-ready shell</span>
          </div>
          <div className="attempt-list">
            {attempts.map((attempt) => (
              <article className="attempt" key={`${attempt.endpoint}-${attempt.event}`}>
                <div>
                  <strong>{attempt.event}</strong>
                  <span>{attempt.endpoint}</span>
                </div>
                <span className={`status ${attempt.status.toLowerCase()}`}>{attempt.status}</span>
                <time>{attempt.time}</time>
              </article>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
