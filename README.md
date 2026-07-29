# Webhook Delivery Platform

A microservices-based webhook delivery platform built with Java/Spring Boot, PostgreSQL, Redis, Kafka, Docker, and React.

## Services

- `backend/services/api-gateway`: external API entry point, tenant API key authentication, admin bootstrap auth, CORS, rate limiting, and service routing.
- `backend/services/webhook-management-service`: tenant, API key, webhook endpoint, and subscription management.
- `backend/services/event-ingestion-service`: validates incoming events, stores payloads, handles idempotency, and publishes delivery jobs.
- `backend/services/delivery-service`: consumes delivery jobs, signs outbound webhook requests, records attempts, schedules retries, and dead-letters exhausted events.
- `backend/services/scheduler-service`: republishes due retry jobs from PostgreSQL into Kafka.
- `frontend/dashboard`: React dashboard for managing endpoints/subscriptions, ingesting test events, and inspecting attempts/retries/dead letters.

## Local Stack

The Docker stack includes PostgreSQL, Flyway, Redis, Kafka, all backend services, and the dashboard.

```powershell
docker compose up -d
```

Default URLs:

- Dashboard: `http://localhost:3000`
- API gateway: `http://localhost:8080`
- Webhook management service: `http://localhost:8083`
- Event ingestion service: `http://localhost:8084`
- Delivery service: `http://localhost:8081`
- Scheduler service: `http://localhost:8082`

Local defaults are embedded in `docker-compose.yml`. To customize ports, database credentials, admin key, or rate limits:

```powershell
Copy-Item .env.example .env
```

## Smoke Test

After the stack is healthy, run:

```powershell
node scripts/smoke-test.mjs
```

Optional environment overrides:

```powershell
$env:API_BASE_URL="http://localhost:8080"
$env:ADMIN_API_KEY="local-admin-key"
$env:WEBHOOK_URL="https://example.com/webhooks"
node scripts/smoke-test.mjs
```

The smoke test creates a tenant, API key, endpoint, subscription, ingests an event, and verifies the created resources can be read back through the gateway.

To smoke test an already seeded tenant:

```powershell
$env:TENANT_ID="11111111-1111-1111-1111-111111111111"
$env:API_KEY="demo-api-key"
node scripts/smoke-test.mjs
```

## Demo Data

To load a repeatable local demo tenant and sample delivery records:

```powershell
docker compose --profile seed up seed-demo
```

Demo dashboard credentials:

- Tenant ID: `11111111-1111-1111-1111-111111111111`
- API key: `demo-api-key`

## Local Development

Backend tests:

```powershell
cd backend/services
mvn test
```

Run one Spring service locally:

```powershell
cd backend/services
mvn -pl api-gateway spring-boot:run
```

Dashboard development server:

```powershell
cd frontend/dashboard
npm.cmd install
npm.cmd run dev
```

Dashboard production build:

```powershell
cd frontend/dashboard
npm.cmd run build
```

## CI

GitHub Actions runs on pushes to `main` and pull requests:

- Backend: Java 21 and `mvn test`
- Frontend: Node 20, `npm ci`, and `npm run build`
- Platform: Docker Compose config validation, seed profile validation, and smoke-test syntax validation
