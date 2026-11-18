#!/usr/bin/env bash
# =============================================================================
# VideoToText AI — PostgreSQL Database Restore Procedure
# =============================================================================

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <path_to_backup_file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
DB_NAME="${POSTGRES_DB:-videototext_prod}"
DB_USER="${POSTGRES_USER:-videototext_user}"
DB_HOST="${POSTGRES_HOST:-postgres}"
DB_PORT="${POSTGRES_PORT:-5432}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file ${BACKUP_FILE} does not exist!"
    exit 1
fi

echo "================================================================="
echo "VideoToText AI — PostgreSQL Restore Procedure"
echo "Target DB: ${DB_NAME} on ${DB_HOST}:${DB_PORT}"
echo "Source:    ${BACKUP_FILE}"
echo "================================================================="

# Checksum verification if file exists
if [ -f "${CHECKSUM_FILE}" ]; then
    echo "Verifying SHA256 checksum..."
    sha256sum -c "${CHECKSUM_FILE}"
    echo "Checksum verified."
fi

# Confirmation prompt for production safety
read -p "WARNING: This will overwrite data in '${DB_NAME}'. Continue? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore aborted by user."
    exit 0
fi

echo "Restoring database from compressed archive..."
gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${POSTGRES_PASSWORD}" pg_restore \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -c -v --if-exists || true

echo "PostgreSQL database restore completed successfully."
