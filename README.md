# Webhook Delivery Platform

A microservices-based webhook delivery platform built with Java 21, Spring Boot, PostgreSQL, Redis, Kafka, Docker, and React.

The project demonstrates a realistic webhook workflow: tenants configure HTTPS endpoints and event subscriptions, events are ingested through an API gateway, delivery jobs are published to Kafka, delivery attempts are signed and recorded, failed attempts are retried, and exhausted deliveries move to a dead-letter queue that can be replayed from the dashboard.

## Architecture

```text
React Dashboard
      |
API Gateway
      |
      +--> Webhook Management Service --> PostgreSQL
      +--> Event Ingestion Service -----> PostgreSQL + Kafka
      +--> Delivery Service ------------> Kafka + PostgreSQL
      +--> Scheduler Service -----------> PostgreSQL + Kafka

Redis is used by the gateway for rate limiting.
Flyway owns database migrations.
```

## Services

- `api-gateway`: routes external traffic, authenticates tenant/admin requests, applies CORS and rate limiting.
- `webhook-management-service`: manages tenants, API keys, webhook endpoints, and subscriptions.
- `event-ingestion-service`: stores incoming events, enforces idempotency, and publishes delivery jobs for matching subscriptions.
- `delivery-service`: consumes delivery jobs, signs webhook requests, records attempts, schedules retries, and manages dead letters.
- `scheduler-service`: republishes due retry jobs from PostgreSQL into Kafka.
- `frontend/dashboard`: React dashboard for local demos and operational inspection.

## Features

- Tenant and API key bootstrap flow
- HTTPS webhook endpoint management
- Event subscription management
- Event ingestion with idempotency keys
- Kafka-based asynchronous delivery
- HMAC-SHA256 webhook signatures
- Delivery attempt history
- Retry queue with manual retry/cancel actions
- Dead-letter queue with clear/replay actions
- Dashboard metrics for events, attempts, endpoints, subscriptions, retries, and dead letters

## Local Setup

Requirements:

- Docker Desktop
- Java 21
- Maven 3.9+
- Node.js 20+

Start the full stack:

```powershell
docker compose up -d --build
```

Open:

- Dashboard: `http://localhost:3000`
- API gateway: `http://localhost:8080`

Useful service URLs:

- Webhook management service: `http://localhost:8083`
- Event ingestion service: `http://localhost:8084`
- Delivery service: `http://localhost:8081`
- Scheduler service: `http://localhost:8082`

To customize local ports, credentials, admin API key, or rate limits:

```powershell
Copy-Item .env.example .env
```

Then edit `.env` and restart Docker Compose.

## Demo Flow

1. Open `http://localhost:3000`.
2. Click `New tenant`.
3. Add a reachable HTTPS webhook endpoint.
4. Add a subscription for an event type such as `order.created`.
5. Send a test event from the dashboard.
6. Select the event to inspect payload and delivery attempts.
7. Use the retry and dead-letter queues to retry, cancel, clear, or replay failed deliveries.

For repeatable demo data:

```powershell
docker compose --profile seed up seed-demo
```

Demo credentials:

- Tenant ID: `11111111-1111-1111-1111-111111111111`
- API key: `demo-api-key`

## Smoke Test

After the stack is healthy:

```powershell
node scripts/smoke-test.mjs
```

Optional overrides:

```powershell
$env:API_BASE_URL="http://localhost:8080"
$env:ADMIN_API_KEY="local-admin-key"
$env:WEBHOOK_URL="https://example.com/webhooks"
node scripts/smoke-test.mjs
```

## Development

Backend tests:

```powershell
cd backend/services
mvn test
```

Frontend checks:

```powershell
cd frontend/dashboard
npm.cmd install
npm.cmd run build
```

Run the dashboard locally:

```powershell
cd frontend/dashboard
npm.cmd run dev
```

Run one backend service locally:

```powershell
cd backend/services
mvn -pl api-gateway spring-boot:run
```

## CI

GitHub Actions runs:

- Java 21 backend tests with Maven
- Node 20 dashboard build
- Docker Compose configuration validation
- Smoke-test syntax validation

## Tech Stack

- Java 21, Spring Boot
- PostgreSQL, Flyway
- Redis
- Kafka
- React, TypeScript, Vite
- Docker Compose
