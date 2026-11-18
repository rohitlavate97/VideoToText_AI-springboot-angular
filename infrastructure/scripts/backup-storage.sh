#!/usr/bin/env bash
# =============================================================================
# VideoToText AI — Object Storage (MinIO / S3) Backup Synchronization
# =============================================================================

set -euo pipefail

BACKUP_DEST="${BACKUP_DEST:-/backups/storage}"
BUCKET_NAME="${MINIO_BUCKET:-videototext}"
S3_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo "================================================================="
echo "Starting Object Storage Backup for bucket: ${BUCKET_NAME}"
echo "Timestamp: ${TIMESTAMP}"
echo "================================================================="

mkdir -p "${BACKUP_DEST}"

# Configure mc alias
mc alias set primary "${S3_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" --api S3v4

# Mirror bucket contents to backup destination
echo "Synchronizing bucket ${BUCKET_NAME} to ${BACKUP_DEST}/${BUCKET_NAME}_${TIMESTAMP}..."
mc mirror "primary/${BUCKET_NAME}" "${BACKUP_DEST}/${BUCKET_NAME}_${TIMESTAMP}"

echo "Object storage backup completed successfully."
