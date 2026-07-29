# Webhook Delivery Platform

A microservices-based webhook delivery platform built with Java/Spring Boot, PostgreSQL, Redis, Kafka, Docker, and React.

## Services

- `backend/services/api-gateway`: external API entry point and routing layer.
- `backend/services/event-ingestion-service`: validates incoming events, stores payloads, and publishes delivery jobs.
- `backend/services/webhook-management-service`: tenant-scoped webhook endpoint and subscription management.
- `backend/services/delivery-service`: consumes webhook delivery jobs from Kafka and performs outbound HTTP delivery.
- `backend/services/scheduler-service`: moves due retries from PostgreSQL into Kafka.
- `frontend/dashboard`: React UI for observing endpoints, events, attempts, and delivery health.

## Local Infrastructure

The local stack includes:

- PostgreSQL for durable webhook configuration, events, and attempts.
- Redis for rate limiting, idempotency windows, and short-lived coordination.
- Kafka for asynchronous delivery and retry dispatch.
- Flyway for repeatable database migrations.

## Getting Started

```powershell
docker compose up -d
```

Run migrations:

```powershell
docker compose run --rm flyway
```

Run the backend services with Docker:

```powershell
docker compose up --build api-gateway event-ingestion-service webhook-management-service delivery-service scheduler-service
```

After installing Java 21 and Maven, run a service:

```powershell
cd backend/services/api-gateway
mvn spring-boot:run
```

For the dashboard:

```powershell
cd frontend/dashboard
npm.cmd install
npm.cmd run dev
```

## Commit Plan

Recommended initial milestones:

1. Scaffold repository and local infrastructure.
2. Implement endpoint registration and event ingestion.
3. Publish delivery jobs to Kafka.
4. Implement delivery worker with retries and attempt tracking.
5. Add dashboard views for events and attempts.
6. Add auth, tenant isolation, rate limits, and observability.
