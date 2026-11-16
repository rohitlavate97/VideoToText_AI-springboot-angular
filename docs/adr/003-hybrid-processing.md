# ADR-003: Hybrid Local and Cloud Processing

**Status**: Accepted  
**Date**: 2026-11-16  
**Context**: Deciding where AI inference runs — locally or in the cloud.

## Decision

Support **both local and cloud processing modes**, selectable per user preference or per job. Default to local for development and privacy-first deployments. Cloud is required for production SLA compliance.

## Context

- The development machine (RTX 3060, 6 GB VRAM) can run small/medium Whisper models but likely cannot meet the 15-minute SLA for a 4-hour video.
- Cloud GPUs (L4 24 GB, L40S 48 GB, A100) can meet the SLA but incur cost.
- Users may have privacy requirements that prohibit sending audio to external services.

## Design

- `processing_mode` is a field on `transcription_jobs` and `user_preferences`.
- Values: `local`, `cloud`.
- The Python AI Worker runs on whatever hardware it is deployed to — the mode controls which worker pool receives the job (local RabbitMQ queue vs. cloud RabbitMQ queue).
- Cloud processing requires explicit user consent (recorded in user preferences).

## Consequences

**Positive**:
- Privacy-first: users control where their data is processed.
- Cost flexibility: local processing is free; cloud is pay-per-use.
- Development works fully offline.

**Negative**:
- Two deployment targets to maintain.
- Local processing cannot guarantee the 15-minute SLA.

**Mitigations**:
- Clearly communicate local processing limitations in the UI.
- Benchmark both modes and publish expected performance.
- Never silently transfer data to cloud without user consent.
