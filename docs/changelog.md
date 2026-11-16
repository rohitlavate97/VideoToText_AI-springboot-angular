# Changelog — VideoToText AI

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Phase 0**: Requirements, architecture, database ERD, API specification, performance strategy, security, testing strategy, ADRs.
- **Phase 1**:
  - Spring Boot 3.5.0 backend initialized with Java 21, Maven 3.9, Flyway, PostgreSQL, Redis, RabbitMQ, S3, JWT, OpenAPI, Testcontainers.
  - Angular 17 frontend initialized with routing, SCSS, Angular Material 17.
  - Python 3.11 AI Worker skeleton initialized with Pydantic settings and Dockerfile.
  - Multi-service `docker-compose.yml` for PostgreSQL 16, RabbitMQ 3.13, Redis 7, and MinIO.
  - Nginx reverse proxy configuration with SSE streaming support.
- **Phase 2**:
  - Package structure across all planned domains (`config`, `security`, `auth`, `user`, `video`, `transcription`, `storage`, `common`).
  - Common DTOs: `ApiResponse<T>`, `ErrorResponse`, `PageResponse<T>`, and `BaseEntity` with UUID PK and JPA auditing hooks.
  - Standardized `ErrorCode` enum and hierarchy of business exceptions (`ResourceNotFoundException`, `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, `ConflictException`, `FileProcessingException`).
  - `@RestControllerAdvice` `GlobalExceptionHandler` with automated validation error mapping, file size limit handling, security exception translation, and sanitized generic error output.
  - JPA Auditing (`JpaConfig`) and complete suite of 15 Flyway migrations (`V1` through `V15`) aligning with the database ERD.
  - RabbitMQ configuration with topic exchange `videototext.exchange`, dead-letter exchange `videototext.dlx`, durable queues, and Jackson JSON message converters.
  - Redis configuration with `RedisTemplate` JSON serialization and `RedisCacheManager` with TTL configuration.
  - OpenAPI 3 / Swagger documentation bean with JWT Bearer security scheme definition.
  - Spring Security baseline configuration with stateless session policy and `BCryptPasswordEncoder` (strength 12).
  - Health check & system runtime metrics endpoints (`/api/v1/health` and `/api/v1/health/system`) with full MockMvc integration tests.
