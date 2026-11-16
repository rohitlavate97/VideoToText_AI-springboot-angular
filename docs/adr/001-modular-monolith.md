# ADR-001: Modular Monolith Architecture

**Status**: Accepted  
**Date**: 2026-11-16  
**Context**: Choosing the backend architecture style for VideoToText AI v1.0.

## Decision

Use a **modular monolith** for the Spring Boot backend rather than microservices.

## Context

The application has clear domain boundaries (auth, video, transcription, translation, export) that could justify microservices. However:

- Solo developer workflow — microservices add significant operational overhead.
- All Java modules share the same PostgreSQL database.
- The only truly independent deployable is the Python AI Worker, which communicates via RabbitMQ.
- v1.0 targets a single-tenant deployment.

## Consequences

**Positive**:
- Simpler deployment (one JAR, one Docker image for the backend).
- Simpler debugging and testing (single process).
- No inter-service network latency or distributed transaction complexity.
- Clean package boundaries allow future extraction into services.

**Negative**:
- All backend modules scale together (cannot scale transcription management independently).
- A bug in one module can crash the entire backend.

**Mitigations**:
- Strict package boundaries with clearly defined interfaces between modules.
- The Python AI Worker is already independently deployable via RabbitMQ.
- If scaling is needed, the backend is stateless and can be horizontally replicated.
