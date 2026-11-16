# Testing Strategy — VideoToText AI

## 1. Test Pyramid

| Level | Framework | Scope |
|-------|-----------|-------|
| **Unit** (Java) | JUnit 5, Mockito | Services, validators, mappers, utilities |
| **Unit** (Python) | pytest, unittest.mock | Providers, processors, mergers, formatters |
| **Unit** (Angular) | Jasmine / Karma | Components, services, pipes |
| **Integration** (Java) | Spring Boot Test, Testcontainers | Database, RabbitMQ, Redis, API endpoints |
| **Integration** (Python) | pytest + Docker deps | Consumer, provider with model, S3 client |
| **E2E** | Playwright | Full user workflows (upload → transcribe → export) |

## 2. Key Test Scenarios

| # | Scenario | Type | Phase |
|---|----------|------|-------|
| 1 | Valid video upload (MP4, MKV, etc.) | Integration | 4 |
| 2 | Invalid file upload (exe, zip) | Integration | 4 |
| 3 | Unsupported format rejection | Integration | 4 |
| 4 | 4-hour video acceptance | Integration | 4 |
| 5 | Video > 4 hours rejection | Integration | 4 |
| 6 | Corrupted video rejection | Integration | 4 |
| 7 | Audio without speech | Integration | 6 |
| 8 | Multiple speakers diarization | Integration | 8 |
| 9 | Hindi transcription | Integration | 6 |
| 10 | Marathi transcription | Integration | 6 |
| 11 | English transcription | Integration | 6 |
| 12 | Mixed-language speech | Integration | 6 |
| 13 | GPU unavailable fallback to CPU | Integration | 6 |
| 14 | Worker crash recovery | Integration | 7 |
| 15 | RabbitMQ interruption handling | Integration | 5 |
| 16 | Chunk processing failure + retry | Integration | 7 |
| 17 | Translation failure handling | Integration | 10 |
| 18 | Large transcript pagination | Integration | 9 |
| 19 | User cancellation mid-processing | Integration | 15 |
| 20 | Concurrent jobs | Performance | 17 |

## 3. Test Data

- Short test videos (10s, 30s, 60s) for unit/integration tests.
- Reference 10-minute video for standard integration tests.
- Reference 4-hour video for performance benchmarks.
- Audio samples in English, Hindi, and Marathi.
- Multi-speaker audio samples.

## 4. CI Test Execution

All tests run in GitHub Actions:
1. Java unit + integration tests (Testcontainers for PostgreSQL, RabbitMQ, Redis).
2. Python unit + integration tests (pytest).
3. Angular unit tests (Karma).
4. E2E tests (Playwright) on scheduled runs (not every push).
