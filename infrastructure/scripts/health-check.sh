#!/usr/bin/env bash
# =============================================================================
# VideoToText AI — Automated Platform Health Inspection
# =============================================================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "================================================================="
echo "VideoToText AI — Production Health Check Inspection"
echo "================================================================="

check_service() {
    local name="$1"
    local url="$2"
    local expected_code="${3:-200}"

    printf "%-30s ... " "$name"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$url" || echo "FAILED")

    if [ "$STATUS" = "$expected_code" ]; then
        printf "${GREEN}[HEALTHY] (HTTP %s)${NC}\n" "$STATUS"
    else
        printf "${RED}[FAILED] (HTTP %s, expected %s)${NC}\n" "$STATUS" "$expected_code"
        return 1
    fi
}

FAILED=0

# Frontend Inspection
check_service "Frontend Web UI" "http://localhost/healthz" || FAILED=1

# Backend API Health
check_service "Backend Actuator Health" "http://localhost:8080/actuator/health" || FAILED=1
check_service "Backend System Health API" "http://localhost:8080/api/v1/health/system" || FAILED=1

# AI Worker Health
check_service "AI Worker Health Probe" "http://localhost:8081/health" || FAILED=1

# Storage (MinIO)
check_service "MinIO Object Storage" "http://localhost:9000/minio/health/ready" || FAILED=1

echo "================================================================="
if [ $FAILED -eq 0 ]; then
    printf "${GREEN}All platform services are operational and healthy.${NC}\n"
    exit 0
else
    printf "${RED}One or more service health checks failed!${NC}\n"
    exit 1
fi
