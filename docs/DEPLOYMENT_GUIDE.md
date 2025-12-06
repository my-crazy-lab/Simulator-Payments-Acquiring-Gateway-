# Deployment Guide

This guide covers deploying the Payment Acquiring Gateway in various environments.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Docker Deployment](#docker-deployment)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [Production Checklist](#production-checklist)
6. [Configuration Reference](#configuration-reference)

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| Java | 17+ | Authorization, Fraud, 3DS, Settlement services |
| Go | 1.21+ | Tokenization, HSM services |
| Rust | 1.70+ | Retry Engine |
| Docker | 24+ | Container runtime |
| Docker Compose | 2.20+ | Local orchestration |
| Maven | 3.9+ | Java build tool |

### Infrastructure Dependencies

| Service | Version | Port | Purpose |
|---------|---------|------|---------|
| PostgreSQL | 15+ | 5432 | Primary database |
| Redis | 7+ | 6379 | Caching, sessions, rate limiting |
| Kafka | 3.5+ | 9092 | Event streaming |
| Schema Registry | 7.5+ | 8081 | Kafka schema management |
| Vault | 1.15+ | 8200 | Secrets management |
| Jaeger | 1.51+ | 16686 | Distributed tracing |
| Prometheus | 2.48+ | 9090 | Metrics collection |
| Grafana | 10.2+ | 3000 | Metrics visualization |

## Local Development

### 1. Start Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d postgres redis kafka zookeeper schema-registry vault jaeger prometheus grafana

# Verify services are healthy
docker-compose ps
```

### 2. Initialize Database

```bash
# Database is auto-initialized from schema.sql
# Verify connection
psql -h localhost -U payments_user -d payment_gateway -c "SELECT 1"
```

### 3. Build Services

```bash
# Build Java services
mvn clean package -DskipTests

# Build Go services
cd hsm-simulator && go build -o bin/hsm-server ./cmd/server && cd ..
cd tokenization-service && go build -o bin/tokenization-server ./cmd/server && cd ..

# Build Rust service
cd retry-engine && cargo build --release && cd ..
```

### 4. Start Services

Start services in order (dependencies first):

```bash
# Terminal 1: HSM Simulator
cd hsm-simulator && ./bin/hsm-server

# Terminal 2: Tokenization Service
cd tokenization-service && ./bin/tokenization-server

# Terminal 3: Fraud Detection Service
cd fraud-detection-service && java -jar target/fraud-detection-service-1.0.0-SNAPSHOT.jar

# Terminal 4: 3D Secure Service
cd threeds-service && java -jar target/threeds-service-1.0.0-SNAPSHOT.jar

# Terminal 5: Retry Engine
cd retry-engine && ./target/release/retry-engine

# Terminal 6: Settlement Service
cd settlement-service && java -jar target/settlement-service-1.0.0-SNAPSHOT.jar

# Terminal 7: Authorization Service (main API)
cd authorization-service && java -jar target/authorization-service-1.0.0-SNAPSHOT.jar
```

### 5. Verify Deployment

```bash
# Check health endpoint
curl http://localhost:8446/actuator/health

# Test payment endpoint (will fail without proper setup, but verifies routing)
curl -X POST http://localhost:8446/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-API-Key: pk_test_demo" \
  -d '{"cardNumber":"4532015112830366","expiryMonth":12,"expiryYear":2025,"cvv":"123","amount":100,"currency":"USD"}'
```

## Docker Deployment

### Build Docker Images

```bash
# Build all service images
docker build -t payment-gateway/authorization-service:latest ./authorization-service
docker build -t payment-gateway/fraud-detection-service:latest ./fraud-detection-service
docker build -t payment-gateway/threeds-service:latest ./threeds-service
docker build -t payment-gateway/settlement-service:latest ./settlement-service
docker build -t payment-gateway/tokenization-service:latest ./tokenization-service
docker build -t payment-gateway/hsm-simulator:latest ./hsm-simulator
docker build -t payment-gateway/retry-engine:latest ./retry-engine
```

### Full Stack Deployment

```bash
# Start everything
docker-compose -f docker-compose.yml -f docker-compose.services.yml up -d

# View logs
docker-compose logs -f authorization-service
```

### Environment Variables

Create `.env` file for Docker deployment:

```env
# Database
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=payment_gateway
POSTGRES_USER=payments_user
POSTGRES_PASSWORD=<secure-password>

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<secure-password>

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SCHEMA_REGISTRY_URL=http://schema-registry:8081

# Vault
VAULT_ADDR=http://vault:8200
VAULT_TOKEN=<vault-token>

# Observability
JAEGER_ENDPOINT=http://jaeger:14268/api/traces
PROMETHEUS_ENDPOINT=http://prometheus:9090

# Service URLs
HSM_SERVICE_URL=hsm-simulator:8444
TOKENIZATION_SERVICE_URL=tokenization-service:8445
FRAUD_SERVICE_URL=fraud-detection-service:8447
THREEDS_SERVICE_URL=threeds-service:8448
RETRY_ENGINE_URL=retry-engine:8450
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (1.28+)
- kubectl configured
- Helm 3.x installed
- Ingress controller (nginx-ingress recommended)
- cert-manager for TLS certificates

### Namespace Setup

```bash
# Create namespaces
kubectl create namespace payment-gateway
kubectl create namespace payment-gateway-pci  # PCI-scoped services

# Apply network policies for PCI isolation
kubectl apply -f k8s/network-policies/
```

### Deploy Infrastructure

```bash
# Add Helm repositories
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add confluentinc https://confluentinc.github.io/cp-helm-charts/
helm repo update

# Deploy PostgreSQL
helm install postgres bitnami/postgresql \
  --namespace payment-gateway \
  --set auth.database=payment_gateway \
  --set auth.username=payments_user \
  --set auth.password=<secure-password> \
  --set primary.persistence.size=100Gi

# Deploy Redis
helm install redis bitnami/redis \
  --namespace payment-gateway \
  --set auth.password=<secure-password> \
  --set replica.replicaCount=3

# Deploy Kafka
helm install kafka bitnami/kafka \
  --namespace payment-gateway \
  --set replicaCount=3 \
  --set zookeeper.replicaCount=3
```

### Deploy Services

```bash
# Apply ConfigMaps and Secrets
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/

# Deploy PCI-scoped services (isolated namespace)
kubectl apply -f k8s/pci-services/ -n payment-gateway-pci

# Deploy non-PCI services
kubectl apply -f k8s/services/ -n payment-gateway

# Deploy Ingress
kubectl apply -f k8s/ingress/
```

### Verify Deployment

```bash
# Check pod status
kubectl get pods -n payment-gateway
kubectl get pods -n payment-gateway-pci

# Check service endpoints
kubectl get svc -n payment-gateway

# View logs
kubectl logs -f deployment/authorization-service -n payment-gateway
```

## Production Checklist

### Security

- [ ] TLS 1.3 enabled for all external endpoints
- [ ] mTLS configured for internal service communication
- [ ] API keys rotated and stored in Vault
- [ ] Database credentials in Vault (not environment variables)
- [ ] Network policies enforcing PCI segmentation
- [ ] WAF configured in front of API Gateway
- [ ] Security headers enabled (CSP, X-Frame-Options, etc.)
- [ ] Rate limiting configured per merchant tier

### High Availability

- [ ] Multiple replicas for each service (minimum 3)
- [ ] Pod disruption budgets configured
- [ ] Database replication enabled (primary + 2 replicas)
- [ ] Redis cluster mode with 3+ nodes
- [ ] Kafka cluster with 3+ brokers
- [ ] Cross-AZ deployment for fault tolerance
- [ ] Health checks and readiness probes configured

### Monitoring

- [ ] Prometheus scraping all service metrics
- [ ] Grafana dashboards imported
- [ ] Alerting rules configured for critical metrics
- [ ] Distributed tracing enabled (Jaeger/Zipkin)
- [ ] Log aggregation configured (ELK/Loki)
- [ ] PagerDuty/OpsGenie integration for alerts

### Backup & Recovery

- [ ] Database backups every 6 hours
- [ ] Backup encryption enabled
- [ ] Cross-region backup replication
- [ ] Disaster recovery runbook documented
- [ ] Recovery time objective (RTO) < 4 hours
- [ ] Recovery point objective (RPO) < 1 hour

### Compliance

- [ ] PCI DSS audit logging enabled
- [ ] Audit logs retained for 7 years
- [ ] Access controls verified (RBAC)
- [ ] Vulnerability scanning scheduled
- [ ] Penetration testing completed
- [ ] PCI DSS SAQ/ROC documentation ready

## Configuration Reference

### Authorization Service

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8446 | HTTP port |
| `spring.datasource.url` | - | PostgreSQL connection URL |
| `spring.data.redis.host` | localhost | Redis host |
| `grpc.client.tokenization.address` | static://localhost:8445 | Tokenization service |
| `grpc.client.fraud-detection.address` | static://localhost:8447 | Fraud service |
| `grpc.client.three-ds.address` | static://localhost:8448 | 3DS service |

### Tokenization Service (PCI Scope)

| Property | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | 8445 | gRPC port |
| `HSM_ADDRESS` | localhost:8444 | HSM service address |
| `DATABASE_URL` | - | PostgreSQL connection |
| `VAULT_ADDR` | - | Vault address |

### HSM Simulator (PCI Scope)

| Property | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | 8444 | gRPC port |
| `KEY_STORAGE_PATH` | /var/lib/hsm/keys | Key storage location |
| `AUDIT_LOG_PATH` | /var/log/hsm/audit.log | Audit log location |

### Retry Engine

| Property | Default | Description |
|----------|---------|-------------|
| `GRPC_PORT` | 8450 | gRPC port |
| `REDIS_URL` | redis://localhost:6379 | Redis connection |
| `MAX_RETRIES` | 5 | Maximum retry attempts |
| `INITIAL_BACKOFF_MS` | 1000 | Initial backoff delay |
| `MAX_BACKOFF_MS` | 60000 | Maximum backoff delay |

### Fraud Detection Service

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8447 | HTTP port |
| `grpc.server.port` | 9447 | gRPC port |
| `fraud.velocity.window-minutes` | 60 | Velocity check window |
| `fraud.score.threshold.review` | 0.5 | Review threshold |
| `fraud.score.threshold.block` | 0.8 | Block threshold |

### 3D Secure Service

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8448 | HTTP port |
| `grpc.server.port` | 9448 | gRPC port |
| `threeds.timeout-seconds` | 300 | Authentication timeout |
| `threeds.challenge.window-size` | 05 | Challenge window size |

### Settlement Service

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8449 | HTTP port |
| `settlement.batch.cron` | 0 0 2 * * * | Batch schedule (2 AM) |
| `settlement.reconciliation.cron` | 0 0 3 * * * | Reconciliation (3 AM) |
