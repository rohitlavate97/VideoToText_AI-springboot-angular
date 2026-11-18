# VideoToText AI — Production Deployment Runbook

This runbook provides step-by-step instructions for deploying, operating, monitoring, backing up, and rolling back VideoToText AI in enterprise production environments.

---

## 1. System Requirements

### 1.1 Compute & Memory Requirements

| Component | Minimum (Staging) | Recommended (Production) | Production (High Concurrency) |
|---|---|---|---|
| **CPU** | 4 vCPUs | 8 vCPUs | 16+ vCPUs |
| **System RAM** | 16 GB | 32 GB | 64 GB |
| **GPU / Accelerator** | Optional (CPU int8 fallback) | 1× NVIDIA L4 or A10G (24 GB VRAM) | 2× NVIDIA L4 / A10G (24 GB VRAM) |
| **Fast NVMe Storage** | 100 GB | 500 GB | 2 TB+ (RAID / EBS gp3) |
| **Host OS** | Ubuntu 22.04 LTS / Debian 12 | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |

### 1.2 Software Prerequisites
- Docker Engine $\ge 24.0$ and Docker Compose $\ge 2.20$
- NVIDIA Container Toolkit (`nvidia-container-toolkit`) installed for GPU worker nodes
- OpenSSL (for certificate and secret generation)
- Domain with DNS A-records pointing to edge reverse proxy / load balancer IP

---

## 2. Configuration & Secrets Setup

### 2.1 Prepare Environment Secrets

Copy the production environment template:
```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

Generate cryptographically secure passwords for every secret in `.env.production`:
```bash
# Generate 32-byte base64 secrets for DB, RabbitMQ, and Redis:
openssl rand -base64 32

# Generate minimum 64-character (512-bit) secret for JWT authentication:
openssl rand -base64 64
```

Verify your `.env.production` contains:
```env
DOMAIN_NAME=videototext.ai
POSTGRES_DB=videototext_prod
POSTGRES_USER=videototext_user
POSTGRES_PASSWORD=<STRONG_POSTGRES_PASSWORD>

RABBITMQ_USER=videototext_admin
RABBITMQ_PASSWORD=<STRONG_RABBITMQ_PASSWORD>
RABBITMQ_VHOST=videototext

REDIS_PASSWORD=<STRONG_REDIS_PASSWORD>

MINIO_ROOT_USER=videototext_s3_admin
MINIO_ROOT_PASSWORD=<STRONG_S3_SECRET_KEY>
MINIO_BUCKET=videototext
S3_ENDPOINT=http://minio:9000

JWT_SECRET=<STRONG_64_CHAR_JWT_SECRET>
ALLOWED_ORIGINS=https://videototext.ai,https://app.videototext.ai
```

---

## 3. SSL/TLS Certificate Setup

### Option A: Let's Encrypt (Certbot)
```bash
sudo apt-get install certbot
sudo certbot certonly --standalone -d videototext.ai -d app.videototext.ai

# Link certificates into docker ssl volume:
sudo mkdir -p ./infrastructure/nginx/ssl
sudo cp /etc/letsencrypt/live/videototext.ai/fullchain.pem ./infrastructure/nginx/ssl/tls.crt
sudo cp /etc/letsencrypt/live/videototext.ai/privkey.pem ./infrastructure/nginx/ssl/tls.key
sudo chmod 600 ./infrastructure/nginx/ssl/tls.key
```

### Option B: Self-Signed Certificates (Staging & Air-Gapped)
```bash
bash infrastructure/nginx/self-signed-ssl.sh
```

---

## 4. Deployment via Production Docker Compose

### 4.1 Launch Platform Services
```bash
# Pull and build production containers
docker compose --env-file .env.production -f docker-compose.prod.yml build

# Start core data services and workers in detached mode
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
```

### 4.2 Verify Container Health Status
```bash
docker compose -f docker-compose.prod.yml ps
```
Expected healthy services:
- `videototext-postgres-prod` (healthy)
- `videototext-rabbitmq-prod` (healthy)
- `videototext-redis-prod` (healthy)
- `videototext-minio-prod` (healthy)
- `videototext-backend-prod` (healthy)
- `videototext-ai-worker-prod` (healthy)
- `videototext-frontend-prod` (healthy)
- `videototext-nginx-prod` (healthy)

### 4.3 Automated Health Inspection Script
```bash
bash infrastructure/scripts/health-check.sh
```

---

## 5. Deployment via Kubernetes (k8s)

For Kubernetes deployments (EKS, GKE, AKS, or on-premise clusters):

```bash
# 1. Create dedicated namespace
kubectl apply -f infrastructure/k8s/00-namespace.yaml

# 2. Apply ConfigMap and Secrets
kubectl apply -f infrastructure/k8s/01-configmap-secrets.yaml

# 3. Deploy stateful data infrastructure
kubectl apply -f infrastructure/k8s/02-postgres.yaml
kubectl apply -f infrastructure/k8s/03-rabbitmq.yaml
kubectl apply -f infrastructure/k8s/04-redis.yaml
kubectl apply -f infrastructure/k8s/05-minio.yaml

# 4. Deploy application microservices
kubectl apply -f infrastructure/k8s/06-backend.yaml
kubectl apply -f infrastructure/k8s/07-ai-worker.yaml
kubectl apply -f infrastructure/k8s/08-frontend.yaml

# 5. Apply Ingress routing and TLS
kubectl apply -f infrastructure/k8s/09-ingress.yaml

# 6. Verify rollout status
kubectl rollout status deployment/backend -n videototext
kubectl rollout status deployment/ai-worker -n videototext
kubectl rollout status deployment/frontend -n videototext
```

---

## 6. Observability & Monitoring Stack

Launch Prometheus and Grafana for real-time telemetry:
```bash
docker compose -f infrastructure/monitoring/docker-compose.monitoring.yml up -d
```
- **Prometheus UI:** `http://<HOST_IP>:9090`
- **Grafana Dashboard:** `http://<HOST_IP>:3000` (Default: `admin` / `videototext_grafana_password`)

The dashboard `VideoToText AI — Production Monitoring` automatically provides:
- Backend HTTP Request Throughput & Status Code distribution
- API Latencies (p50, p95, p99)
- RabbitMQ Queue Backlogs (`transcription.jobs`) & Dead Letters (`transcription.dlq`)
- AI Worker CPU, GPU, and RAM consumption
- JVM Heap and Garbage Collection telemetry

---

## 7. Backup and Restore Procedures

### 7.1 PostgreSQL Database Backup
Automated daily cron setup:
```bash
# Run backup script manually
bash infrastructure/scripts/backup-database.sh

# Cron configuration for daily backup at 02:00 AM:
0 2 * * * /bin/bash /opt/videototext/infrastructure/scripts/backup-database.sh >> /var/log/videototext-backup.log 2>&1
```

### 7.2 PostgreSQL Database Restore
```bash
bash infrastructure/scripts/restore-database.sh /backups/postgres/videototext_prod_20261118_120000.sql.gz
```

### 7.3 Object Storage (S3 / MinIO) Backup & Mirroring
```bash
bash infrastructure/scripts/backup-storage.sh
```

### 7.4 Object Storage Restore
```bash
bash infrastructure/scripts/restore-storage.sh /backups/storage/videototext_20261118_120000
```

---

## 8. Rollback and Disaster Recovery Procedures

### 8.1 Zero-Downtime Rollback (Docker Compose)
If a release causes unexpected regressions:
1. Re-tag the prior stable image or update image tags in `.env.production`:
   ```bash
   sed -i 's/latest/v1.0.0-stable/' .env.production
   ```
2. Redeploy without downtime:
   ```bash
   docker compose -f docker-compose.prod.yml up -d --no-deps backend frontend ai-worker
   ```
3. Verify system health:
   ```bash
   bash infrastructure/scripts/health-check.sh
   ```

### 8.2 Kubernetes Rollback
```bash
kubectl rollout undo deployment/backend -n videototext
kubectl rollout undo deployment/ai-worker -n videototext
kubectl rollout undo deployment/frontend -n videototext
```

---

## 9. Troubleshooting & FAQ

| Symptom | Probable Cause | Remediation |
|---|---|---|
| **GPU not detected in worker** | NVIDIA container toolkit missing | Run `nvidia-smi` on host and ensure `nvidia-container-toolkit` is configured in `/etc/docker/daemon.json`. |
| **RabbitMQ connection refused** | Broker initialization delay | Inspect `docker logs videototext-rabbitmq-prod`. Backend automatically reconnects with exponential backoff. |
| **SSE stream times out** | Reverse proxy buffering enabled | Ensure Nginx has `proxy_buffering off;` and `proxy_read_timeout 24h;` for `/api/v1/events/`. |
| **MinIO 403 Access Denied** | Bucket policy uninitialized | Run `minio-init` container or verify `mc anonymous set download myminio/videototext`. |
