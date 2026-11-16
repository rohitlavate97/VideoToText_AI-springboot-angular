# Security Requirements — VideoToText AI

## 1. Authentication

| Requirement | Implementation |
|-------------|----------------|
| User registration | Email + password with validation |
| Password storage | bcrypt with cost factor ≥ 12 |
| Authentication | JWT (access token + refresh token) |
| Access token lifetime | 15 minutes |
| Refresh token lifetime | 7 days (rotated on use) |
| Token storage (frontend) | HttpOnly cookie or secure localStorage |
| Logout | Invalidate refresh token server-side |

## 2. Authorization

| Requirement | Implementation |
|-------------|----------------|
| Role model | USER, ADMIN |
| Resource ownership | Users can only access their own videos/transcripts |
| Admin access | Full access to all resources + system management |
| API protection | All endpoints require valid JWT except login/register/health |
| Method-level security | `@PreAuthorize` annotations on service methods |

## 3. File Upload Security

| Threat | Mitigation |
|--------|------------|
| Malicious file upload | Validate file extension, Content-Type header, and magic bytes |
| Executable disguised as video | Check file signatures (MP4: `ftyp`, MKV: `1A 45 DF A3`, etc.) |
| Path traversal | Sanitize filenames; use UUID-based storage keys |
| Zip bomb / decompression bomb | Limit file size; validate with FFprobe before full processing |
| Oversized files | Configurable max file size (default 10 GB) |
| Excessive uploads | Per-user upload quotas (configurable) |

## 4. Processing Security

| Threat | Mitigation |
|--------|------------|
| FFmpeg command injection | Use parameterized process builder; never interpolate user input into commands |
| Resource exhaustion | Limit concurrent jobs per user; timeout processing stages |
| GPU memory exhaustion | Monitor VRAM; conservative batch sizes; graceful OOM handling |
| Uncontrolled external calls | AI worker only communicates via RabbitMQ; no direct external API calls without explicit configuration |

## 5. Data Privacy

| Requirement | Implementation |
|-------------|----------------|
| No silent cloud transfer | Processing mode (local/cloud) explicitly configured; user consent required for cloud processing |
| Data at rest | Object storage encryption |
| Data in transit | HTTPS/TLS for all external communication |
| Data retention | Configurable retention periods; hard deletion capability |
| Audit logging | Log security events (login, upload, delete, admin actions) |

## 6. API Security

| Requirement | Implementation |
|-------------|----------------|
| Rate limiting | Configurable per-endpoint rate limits (e.g., 100 req/min for API, 5 req/min for upload) |
| Input validation | Spring Validation on all DTOs; reject unexpected fields |
| CORS | Restrict to allowed origins |
| Error handling | Never expose stack traces or internal details in API responses |
| Request size limits | Configurable max request body size |
| Security headers | X-Content-Type-Options, X-Frame-Options, X-XSS-Protection, Content-Security-Policy |

## 7. Infrastructure Security

| Requirement | Implementation |
|-------------|----------------|
| Secrets management | Environment variables; never in source code or Git |
| Database access | Restricted credentials per service; no shared root access |
| RabbitMQ access | Dedicated credentials; vhost isolation |
| Redis access | Password-protected; no public exposure |
| Network segmentation | Internal services not exposed to public internet |
| Container security | Non-root container users; read-only filesystems where possible |
| Dependency scanning | Regular dependency audit (Maven, npm, pip) |

## 8. Compliance Considerations

- GDPR-style data subject rights: ability to export and delete all user data.
- Retain processing logs for audit but not raw audio/video beyond retention period.
- Ensure all third-party AI models have acceptable licenses for the intended use case.
- Document data flow for privacy impact assessment.
