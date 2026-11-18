#!/usr/bin/env bash
# =============================================================================
# VideoToText AI — Object Storage (MinIO / S3) Restore
# =============================================================================

set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <path_to_backup_directory>"
    exit 1
fi

BACKUP_SOURCE="$1"
BUCKET_NAME="${MINIO_BUCKET:-videototext}"
S3_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"

if [ ! -d "${BACKUP_SOURCE}" ]; then
    echo "ERROR: Backup directory ${BACKUP_SOURCE} does not exist!"
    exit 1
fi

echo "================================================================="
echo "VideoToText AI — Object Storage Restore Procedure"
echo "Source: ${BACKUP_SOURCE}"
echo "Target: ${S3_ENDPOINT}/${BUCKET_NAME}"
echo "================================================================="

read -p "WARNING: This will restore/overwrite files in '${BUCKET_NAME}'. Continue? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore aborted by user."
    exit 0
fi

# Configure mc alias
mc alias set primary "${S3_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" --api S3v4

# Mirror backup directory to primary bucket
mc mirror "${BACKUP_SOURCE}" "primary/${BUCKET_NAME}"

echo "Object storage restore completed successfully."
