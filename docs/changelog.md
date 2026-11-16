# Changelog — VideoToText AI

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Phase 0**: Requirements, architecture, database ERD, API specification, performance strategy, security, testing strategy, ADRs.
- **Phase 1**:
  - Spring Boot 3.5.0 backend initialized with Java 21, Maven 3.9, Flyway, PostgreSQL, Redis, RabbitMQ, S3, JWT, OpenAPI, Testcontainers, and passing Spring context unit test.
  - Angular 17 frontend initialized with routing, SCSS, Angular Material 17, and verified production build.
  - Python 3.11 AI Worker skeleton initialized with Pydantic settings, worker lifecycle structure, and Dockerfile.
  - Multi-service `docker-compose.yml` for PostgreSQL 16, RabbitMQ 3.13, Redis 7, and MinIO with auto-bucket initialization.
  - Nginx reverse proxy configuration with SSE streaming support.
  - Root `.gitignore`, `.env.example`, and comprehensive `README.md`.
