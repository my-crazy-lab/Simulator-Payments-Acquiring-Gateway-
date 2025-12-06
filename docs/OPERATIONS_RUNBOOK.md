# Operations Runbook

This runbook provides procedures for operating and maintaining the Payment Acquiring Gateway.

## Table of Contents

1. [Service Overview](#service-overview)
2. [Health Monitoring](#health-monitoring)
3. [Common Operations](#common-operations)
4. [Incident Response](#incident-response)
5. [Maintenance Procedures](#maintenance-procedures)
6. [Scaling Procedures](#scaling-procedures)

## Service Overview

### Service Ports

| Service | HTTP Port | gRPC Port | Metrics Port |
|---------|-----------|-----------|--------------|
| Authorization Service | 8446 | - | 8446/actuator |
| Tokenization Service | - | 8445 | 8445/metrics |
| HSM Simulator | - | 8444 | 8444/metrics |
| Fraud Detection | 8447 | 9447 | 8447/actuator |
| 3D Secure | 8448 | 9448 | 8448/actuator |
| Settlement | 8449 | - | 8449/actuator |
| Retry Engine | - | 8450 | 8450/metrics |

### Service Dependencies

```
Authorization Service
├── Tokenization Service (gRPC) [REQUIRED]
│   └── HSM Simulator (gRPC) [REQUIRED]
├── Fraud Detection Service (gRPC) [DEGRADABLE]
├── 3D Secure Service (gRPC) [DEGRADABLE]
├── Retry Engine (gRPC) [DEGRADABLE]
├── PostgreSQL [REQUIRED]
├── Redis [DEGRADABLE]
└── Kafka [DEGRADABLE]
```

## Health Monitoring

### Health Check Endpoints

```bash
# Authorization Service
curl http://localhost:8446/actuator/health

# Fraud Detection Service
curl http://localhost:8447/actuator/health

# 3D Secure Service
curl http://localhost:8448/actuator/health

# Settlement Service
curl http://localhost:8449/actuator/health
```

### Key Metrics to Monitor

#### Payment Processing
- `payments.processed` - Total payments processed
- `payments.processing.time` - Payment processing latency (p50, p95, p99)
- `payments.success.rate` - Payment success rate
- `payments.by.status` - Payments by status (authorized, captured, failed)

#### Service Health
- `http.server.requests` - HTTP request metrics
- `jvm.memory.used` - JVM memory usage
- `db.pool.active` - Database connection pool usage
- `redis.connections.active` - Redis connection count

#### Business Metrics
- `fraud.score.distribution` - Fraud score distribution
- `threeds.authentication.rate` - 3DS authentication success rate
- `settlement.batch.size` - Settlement batch sizes
- `refund.rate` - Refund rate by merchant

### Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Payment success rate | < 98% | < 95% |
| P95 latency | > 400ms | > 500ms |
| Error rate | > 1% | > 5% |
| Database connections | > 80% | > 95% |
| Memory usage | > 80% | > 95% |
| Disk usage | > 80% | > 95% |

## Common Operations

### Viewing Logs

```bash
# Docker Compose
docker-compose logs -f authorization-service
docker-compose logs -f --tail=100 fraud-detection-service

# Kubernetes
kubectl logs -f deployment/authorization-service -n payment-gateway
kubectl logs -f deployment/authorization-service -n payment-gateway --previous  # Previous container

# Filter by log level
kubectl logs deployment/authorization-service -n payment-gateway | grep ERROR
```

### Checking Service Status

```bash
# Docker Compose
docker-compose ps

# Kubernetes
kubectl get pods -n payment-gateway -o wide
kubectl describe pod <pod-name> -n payment-gateway
```

### Restarting Services

```bash
# Docker Compose - Single service
docker-compose restart authorization-service

# Docker Compose - All services
docker-compose restart

# Kubernetes - Rolling restart
kubectl rollout restart deployment/authorization-service -n payment-gateway

# Kubernetes - Force restart (delete pods)
kubectl delete pods -l app=authorization-service -n payment-gateway
```

### Database Operations

```bash
# Connect to database
psql -h localhost -U payments_user -d payment_gateway

# Check active connections
SELECT count(*) FROM pg_stat_activity WHERE datname = 'payment_gateway';

# Check long-running queries
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';

# Kill long-running query
SELECT pg_terminate_backend(<pid>);
```

### Redis Operations

```bash
# Connect to Redis
redis-cli -h localhost -p 6379 -a <password>

# Check memory usage
INFO memory

# Check connected clients
CLIENT LIST

# Clear rate limit keys (emergency only)
KEYS "rate_limit:*" | xargs redis-cli DEL

# Clear idempotency keys (emergency only)
KEYS "idempotency:*" | xargs redis-cli DEL
```

### Kafka Operations

```bash
# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics --bootstrap-server localhost:9092 --describe --topic payment-events

# Check consumer lag
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group settlement-consumer

# Reset consumer offset (use with caution)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group settlement-consumer \
  --topic payment-events \
  --reset-offsets --to-earliest --execute
```

## Incident Response

### High Error Rate

**Symptoms:**
- Payment success rate dropping
- Increased error responses (4xx, 5xx)
- Alert: "Payment error rate > 5%"

**Investigation:**
```bash
# Check service logs for errors
kubectl logs deployment/authorization-service -n payment-gateway | grep ERROR | tail -50

# Check PSP connectivity
curl -v https://api.stripe.com/v1/charges -H "Authorization: Bearer sk_test_xxx"

# Check database connectivity
psql -h <db-host> -U payments_user -d payment_gateway -c "SELECT 1"

# Check Redis connectivity
redis-cli -h <redis-host> ping
```

**Resolution:**
1. Identify failing component (PSP, database, internal service)
2. If PSP issue: Check PSP status page, consider failover
3. If database issue: Check connections, restart if needed
4. If internal service: Restart affected service
5. If widespread: Consider enabling degraded mode

### High Latency

**Symptoms:**
- P95 latency > 500ms
- Timeouts in logs
- Alert: "Payment latency critical"

**Investigation:**
```bash
# Check distributed traces
# Open Jaeger UI: http://localhost:16686
# Search for slow traces (> 500ms)

# Check database query performance
SELECT query, calls, mean_time, total_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

# Check Redis latency
redis-cli --latency

# Check service resource usage
kubectl top pods -n payment-gateway
```

**Resolution:**
1. Identify slow component from traces
2. If database: Check for missing indexes, long-running queries
3. If Redis: Check memory, consider scaling
4. If external service: Check network, consider caching
5. Scale affected service if resource-constrained

### Service Unavailable

**Symptoms:**
- Health check failing
- Connection refused errors
- Alert: "Service down"

**Investigation:**
```bash
# Check pod status
kubectl get pods -n payment-gateway
kubectl describe pod <pod-name> -n payment-gateway

# Check events
kubectl get events -n payment-gateway --sort-by='.lastTimestamp'

# Check resource limits
kubectl describe node <node-name>
```

**Resolution:**
1. Check if pod is in CrashLoopBackOff - check logs for startup errors
2. Check if pod is Pending - check resource availability
3. Check if pod is OOMKilled - increase memory limits
4. Restart pod if stuck
5. Scale up if resource-constrained

### Database Connection Exhaustion

**Symptoms:**
- "Connection pool exhausted" errors
- Slow queries
- Alert: "Database connections > 95%"

**Investigation:**
```bash
# Check active connections
SELECT count(*), state FROM pg_stat_activity 
WHERE datname = 'payment_gateway' 
GROUP BY state;

# Check connection sources
SELECT client_addr, count(*) 
FROM pg_stat_activity 
WHERE datname = 'payment_gateway' 
GROUP BY client_addr;

# Check for idle connections
SELECT pid, usename, state, query_start, query
FROM pg_stat_activity
WHERE datname = 'payment_gateway' AND state = 'idle'
ORDER BY query_start;
```

**Resolution:**
1. Identify service with most connections
2. Check for connection leaks in application
3. Terminate idle connections if safe
4. Increase connection pool size temporarily
5. Scale database if persistent issue

### Circuit Breaker Open

**Symptoms:**
- PSP requests failing immediately
- "Circuit breaker open" in logs
- Alert: "PSP circuit breaker open"

**Investigation:**
```bash
# Check circuit breaker status
curl http://localhost:8450/api/v1/circuits

# Check PSP health
curl -v https://api.stripe.com/v1/charges

# Check recent PSP errors
kubectl logs deployment/authorization-service -n payment-gateway | grep "PSP" | tail -50
```

**Resolution:**
1. Check PSP status page for outages
2. If PSP is healthy, circuit will auto-recover
3. If PSP is down, traffic routes to backup PSP
4. Manual circuit reset (emergency only):
   ```bash
   redis-cli DEL "circuit:stripe"
   ```

## Maintenance Procedures

### Database Maintenance

```bash
# Vacuum and analyze (run during low traffic)
psql -h <db-host> -U payments_user -d payment_gateway -c "VACUUM ANALYZE;"

# Reindex (run during maintenance window)
psql -h <db-host> -U payments_user -d payment_gateway -c "REINDEX DATABASE payment_gateway;"

# Check table sizes
SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

### Log Rotation

Logs are automatically rotated by the logging infrastructure. Manual cleanup:

```bash
# Docker - prune old logs
docker system prune --volumes

# Kubernetes - logs managed by cluster
# Check log retention policy in logging stack
```

### Certificate Rotation

```bash
# Check certificate expiry
openssl s_client -connect api.paymentgateway.example.com:443 2>/dev/null | openssl x509 -noout -dates

# Rotate certificates (cert-manager handles automatically)
kubectl delete certificate payment-gateway-tls -n payment-gateway
# cert-manager will recreate
```

### Key Rotation

```bash
# HSM key rotation (scheduled quarterly)
# 1. Generate new key version
grpcurl -plaintext localhost:8444 hsm.HSMService/RotateKey

# 2. Update key version in services
kubectl set env deployment/tokenization-service KEY_VERSION=2 -n payment-gateway-pci

# 3. Verify new key is active
grpcurl -plaintext localhost:8444 hsm.HSMService/GetKeyInfo
```

## Scaling Procedures

### Horizontal Scaling

```bash
# Scale deployment
kubectl scale deployment authorization-service --replicas=5 -n payment-gateway

# Auto-scaling (HPA)
kubectl autoscale deployment authorization-service \
  --min=3 --max=10 --cpu-percent=70 -n payment-gateway

# Check HPA status
kubectl get hpa -n payment-gateway
```

### Vertical Scaling

```bash
# Update resource limits
kubectl set resources deployment authorization-service \
  --limits=cpu=2,memory=4Gi \
  --requests=cpu=1,memory=2Gi \
  -n payment-gateway
```

### Database Scaling

```bash
# Add read replica (managed database)
# Use cloud provider console or CLI

# Promote replica to primary (failover)
# Use cloud provider failover procedure
```

### Redis Scaling

```bash
# Scale Redis cluster
# Add nodes through Redis cluster management

# Check cluster status
redis-cli cluster info
redis-cli cluster nodes
```
