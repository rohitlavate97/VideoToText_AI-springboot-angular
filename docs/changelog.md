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
  - Modular package structure across all domains.
  - Common DTOs (`ApiResponse`, `ErrorResponse`, `PageResponse`) and `BaseEntity` with JPA callbacks.
  - Standardized `ErrorCode` enum and `GlobalExceptionHandler` with automatic validation error mapping.
  - Complete baseline suite of 15 Flyway migrations (`V1` to `V15`).
  - RabbitMQ topic and dead-letter configurations, Redis template/cache manager, OpenAPI Swagger docs, and health checks.
- **Phase 3**:
  - `User`, `Role`, and `UserPreference` JPA entities with EAGER role fetching and audit timestamps.
  - `UserRepository`, `RoleRepository`, and `UserPreferenceRepository`.
  - `JwtTokenProvider` using JJWT 0.12.x for HMAC-SHA512 access token (15 mins) and refresh token (7 days) generation and validation.
  - `JwtAuthenticationFilter` and `JwtAuthenticationEntryPoint` wired into `SecurityFilterChain` with stateless session management.
  - `AuthService` handling registration with password strength regex validation, BCrypt hashing (strength 12), login, refresh token rotation, and Redis revocation tracking.
  - `UserService` managing profile updates and language/engine/mode preferences.
  - `AuthController` (`/api/v1/auth/register`, `/login`, `/refresh`, `/logout`) and `UserController` (`/api/v1/users/me`, `/me/preferences`).
  - Full MockMvc integration test suite covering registration, duplicate rejection, weak password rejection, authentication, refresh token rotation, and profile access control (19/19 tests passing).
  - Angular 17 Frontend Authentication UI:
    - Angular Material Login and Registration components with reactive forms and error handling.
    - Authenticated Dashboard with app toolbar, user menu, and quick action cards.
    - User Profile & Preferences editor for configuring default language, processing mode (local/cloud), and ASR engine/model.
    - Functional `authInterceptor` with automatic token attachment and 401 refresh token retry logic.
    - `authGuard` and `guestGuard` route protection with lazy loading.
