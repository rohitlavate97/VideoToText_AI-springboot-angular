# ADR-004: RabbitMQ for Async Processing

**Status**: Accepted  
**Date**: 2026-11-16  
**Context**: Choosing the communication mechanism between the Spring Boot backend and the Python AI Worker.

## Decision

Use **RabbitMQ** as the message broker for all backend–worker communication.

## Alternatives Considered

| Option | Pros | Cons |
|--------|------|------|
| **RabbitMQ** | Mature, reliable delivery, DLQ support, publisher confirms, consumer ack, routing flexibility | Additional infrastructure component |
| Direct HTTP (FastAPI) | Simple request-response | Tight coupling, no retry/DLQ, worker unavailability blocks backend |
| Redis Streams | Already have Redis | Less mature as job queue, no built-in DLQ, limited routing |
| Apache Kafka | High throughput, replay | Overkill for job queue; operational complexity |
| gRPC | Low latency, typed contracts | Tight coupling, no built-in persistence |

## Rationale

- Transcription jobs are long-running (minutes to tens of minutes). Synchronous HTTP would require very long timeouts.
- RabbitMQ provides message persistence, consumer acknowledgment, and dead-letter routing — critical for job reliability.
- The worker can be independently scaled, restarted, or upgraded without affecting the backend.
- Failed messages are routed to a dead-letter queue for inspection and retry.

## Consequences

**Positive**:
- Backend and worker are fully decoupled.
- Jobs survive worker restarts (persistent queues).
- Built-in retry with exponential backoff via DLQ + TTL.
- Worker can run on different hardware (local GPU or cloud GPU).

**Negative**:
- Additional infrastructure component to deploy and monitor.
- Eventual consistency — backend doesn't know job result immediately.

**Mitigations**:
- RabbitMQ runs in Docker for local development (minimal overhead).
- Processing events are published back through RabbitMQ → SSE to keep the UI informed.
- Health checks monitor RabbitMQ connectivity.
