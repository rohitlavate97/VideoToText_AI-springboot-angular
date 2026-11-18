#!/usr/bin/env bash
# =============================================================================
# VideoToText AI — Automated PostgreSQL Database Backup
# =============================================================================

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups/postgres}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_NAME="${POSTGRES_DB:-videototext_prod}"
DB_USER="${POSTGRES_USER:-videototext_user}"
DB_HOST="${POSTGRES_HOST:-postgres}"
DB_PORT="${POSTGRES_PORT:-5432}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

mkdir -p "${BACKUP_DIR}"

BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql.gz"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

echo "================================================================="
echo "Starting PostgreSQL backup: ${DB_NAME} at ${TIMESTAMP}"
echo "================================================================="

# Execute pg_dump and pipe through gzip
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -F c \
  -b -v \
  | gzip -9 > "${BACKUP_FILE}"

# Generate SHA256 checksum for verification
sha256sum "${BACKUP_FILE}" > "${CHECKSUM_FILE}"

echo "Backup created successfully: ${BACKUP_FILE}"
echo "Checksum: $(cat "${CHECKSUM_FILE}")"

# Retention policy: remove backups older than RETENTION_DAYS
echo "Applying retention policy: removing backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -type f -name "${DB_NAME}_*.sql.gz*" -mtime +"${RETENTION_DAYS}" -delete

echo "PostgreSQL backup completed successfully."
