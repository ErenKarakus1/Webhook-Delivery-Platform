# Webhook Delivery Platform

A microservices-based webhook delivery platform built with Java/Spring Boot, PostgreSQL, Redis, Kafka, Docker, and React.

## Services

- `api-gateway`: external API entry point for tenants, endpoints, and event ingestion.
- `delivery-service`: consumes webhook delivery jobs from Kafka and performs outbound HTTP delivery.
- `scheduler-service`: moves due retries from PostgreSQL into Kafka.
- `dashboard`: React UI for observing endpoints, events, attempts, and delivery health.

## Local Infrastructure

The local stack includes:

- PostgreSQL for durable webhook configuration, events, and attempts.
- Redis for rate limiting, idempotency windows, and short-lived coordination.
- Kafka for asynchronous delivery and retry dispatch.

## Getting Started

```powershell
docker compose up -d
```

After installing Java 21 and Maven, run a service:

```powershell
cd services/api-gateway
mvn spring-boot:run
```

For the dashboard:

```powershell
cd apps/dashboard
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
