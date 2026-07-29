# Architecture

## Core Flow

1. A tenant registers webhook endpoints in `api-gateway`.
2. A producer submits an event to `api-gateway`.
3. `api-gateway` stores the event and publishes a delivery job to Kafka.
4. `delivery-service` consumes the job, signs the request, and calls the subscriber endpoint.
5. Each attempt is stored in PostgreSQL.
6. Failed attempts are scheduled for retry with exponential backoff.
7. `scheduler-service` publishes due retry jobs back to Kafka.

## Data Ownership

- `api-gateway` owns tenants, endpoint configuration, and event ingestion.
- `delivery-service` owns outbound attempts and delivery result recording.
- `scheduler-service` owns retry dispatch decisions.
- PostgreSQL is shared at the database level for local simplicity; schemas separate service ownership.

## Kafka Topics

- `webhook.delivery.requested`: delivery jobs ready to be attempted.
- `webhook.delivery.retry-due`: retry jobs emitted by the scheduler.
- `webhook.delivery.completed`: optional delivery status events for projections and dashboard updates.

## Retry Policy

Default retry delays:

- 1 minute
- 5 minutes
- 15 minutes
- 1 hour
- 6 hours
- 24 hours

Retries should stop when the endpoint returns a 2xx response or the configured max attempt count is reached.
