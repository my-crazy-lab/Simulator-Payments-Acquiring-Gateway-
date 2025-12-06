# Troubleshooting Guide

This guide helps diagnose and resolve common issues with the Payment Acquiring Gateway.

## Table of Contents

1. [Payment Issues](#payment-issues)
2. [Authentication Issues](#authentication-issues)
3. [Service Issues](#service-issues)
4. [Database Issues](#database-issues)
5. [Performance Issues](#performance-issues)
6. [Integration Issues](#integration-issues)

## Payment Issues

### Payment Declined

**Symptoms:**
- Payment returns `FAILED` status
- Error code indicates decline

**Common Causes & Solutions:**

| Error Code | Cause | Solution |
|------------|-------|----------|
| `INSUFFICIENT_FUNDS` | Card has insufficient balance | Customer should use different card |
| `CARD_EXPIRED` | Card expiration date passed | Customer should update card |
| `INVALID_CVV` | CVV doesn't match | Customer should verify CVV |
| `CARD_DECLINED` | Issuer declined | Customer should contact bank |
| `FRAUD_SUSPECTED` | Fraud detection triggered | Review fraud rules, contact support |

**Debugging Steps:**
```bash
# Check payment details
curl https://api.paymentgateway.example.com/api/v1/payments/{paymentId} \
  -H "X-API-Key: your_api_key"

# Check audit logs for payment
# Look for fraud score, 3DS status, PSP response
```

### Payment Stuck in PENDING

**Symptoms:**
- Payment status remains `PENDING`
- No authorization response

**Possible Causes:**
1. PSP timeout
2. 3DS authentication pending
3. Service communication failure

**Solutions:**
```bash
# Check if 3DS is pending
# Look for threeDsStatus in payment response

# Check PSP connectivity
curl -v https://api.stripe.com/v1/charges

# Check service health
curl http://localhost:8446/actuator/health
```

### Duplicate Payments

**Symptoms:**
- Customer charged multiple times
- Multiple payments for same order

**Cause:** Missing or incorrect idempotency key

**Solution:**
```bash
# Always use idempotency keys
curl -X POST https://api.paymentgateway.example.com/api/v1/payments \
  -H "Idempotency-Key: order-12345-v1" \
  -H "X-API-Key: your_api_key" \
  -d '...'

# Check for existing payment with same idempotency key
# System returns cached response if key exists
```

### Refund Failed

**Symptoms:**
- Refund returns error
- Refund stuck in PENDING

**Common Causes:**

| Error | Cause | Solution |
|-------|-------|----------|
| `REFUND_EXCEEDS_AMOUNT` | Refund > original amount | Check total refunded |
| `PAYMENT_NOT_CAPTURED` | Payment not yet captured | Void instead of refund |
| `PAYMENT_ALREADY_REFUNDED` | Full refund already done | Check refund history |

**Debugging:**
```bash
# Check original payment status
curl https://api.paymentgateway.example.com/api/v1/payments/{paymentId}

# Check existing refunds
curl "https://api.paymentgateway.example.com/api/v1/transactions?paymentId={paymentId}"
```

## Authentication Issues

### Invalid API Key

**Symptoms:**
- 401 Unauthorized response
- "Invalid API key" error

**Solutions:**
1. Verify API key format (`pk_live_xxx` or `pk_test_xxx`)
2. Check key hasn't been revoked
3. Ensure using correct environment (test vs live)
4. Check for extra whitespace in key

```bash
# Test API key
curl -v https://api.paymentgateway.example.com/api/v1/health \
  -H "X-API-Key: pk_test_your_key"

# Check response headers for error details
```

### Token Expired

**Symptoms:**
- 401 Unauthorized with "Token expired"
- OAuth token no longer valid

**Solution:**
```bash
# Refresh OAuth token
curl -X POST https://auth.paymentgateway.example.com/oauth/token \
  -d "grant_type=refresh_token&refresh_token=YOUR_REFRESH_TOKEN"

# Or get new token
curl -X POST https://auth.paymentgateway.example.com/oauth/token \
  -d "grant_type=client_credentials&client_id=ID&client_secret=SECRET"
```

### Rate Limited

**Symptoms:**
- 429 Too Many Requests
- `Retry-After` header in response

**Solutions:**
1. Implement exponential backoff
2. Check rate limit headers
3. Contact support for limit increase

```bash
# Check rate limit headers
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1701864000
Retry-After: 60

# Wait and retry
sleep 60
# Retry request
```

## Service Issues

### Service Unavailable

**Symptoms:**
- 503 Service Unavailable
- Connection refused
- Timeout errors

**Debugging Steps:**

```bash
# 1. Check service health
curl http://localhost:8446/actuator/health

# 2. Check if service is running
docker-compose ps
# or
kubectl get pods -n payment-gateway

# 3. Check service logs
docker-compose logs authorization-service
# or
kubectl logs deployment/authorization-service -n payment-gateway

# 4. Check dependencies
curl http://localhost:8447/actuator/health  # Fraud
curl http://localhost:8448/actuator/health  # 3DS
```

### Tokenization Service Down

**Symptoms:**
- Payments fail with tokenization error
- "Unable to tokenize card" message

**Impact:** All new payments will fail (critical)

**Solutions:**
```bash
# 1. Check tokenization service
kubectl get pods -l app=tokenization-service -n payment-gateway-pci

# 2. Check HSM connectivity
kubectl logs deployment/tokenization-service -n payment-gateway-pci | grep HSM

# 3. Restart if needed
kubectl rollout restart deployment/tokenization-service -n payment-gateway-pci

# 4. Check HSM service
kubectl get pods -l app=hsm-simulator -n payment-gateway-pci
```

### Fraud Service Degraded

**Symptoms:**
- Payments processed without fraud scoring
- "Fraud service unavailable" in logs

**Impact:** Payments continue with basic fraud checks (degraded mode)

**Solutions:**
```bash
# 1. Check fraud service
kubectl get pods -l app=fraud-detection-service -n payment-gateway

# 2. Check Redis (velocity checks)
redis-cli -h redis ping

# 3. Restart fraud service
kubectl rollout restart deployment/fraud-detection-service -n payment-gateway
```

### Circuit Breaker Open

**Symptoms:**
- PSP requests failing immediately
- "Circuit breaker open" in logs

**Solutions:**
```bash
# 1. Check circuit status
curl http://localhost:8450/api/v1/circuits

# 2. Check PSP status
# Visit PSP status page (e.g., status.stripe.com)

# 3. Wait for auto-recovery (circuit will half-open after timeout)

# 4. Manual reset (emergency only)
redis-cli DEL "circuit:stripe"
```

## Database Issues

### Connection Pool Exhausted

**Symptoms:**
- "Unable to acquire connection" errors
- Slow response times
- Timeouts

**Solutions:**
```bash
# 1. Check active connections
psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = 'payment_gateway';"

# 2. Check for idle connections
psql -c "SELECT pid, state, query_start, query FROM pg_stat_activity WHERE datname = 'payment_gateway' AND state = 'idle' ORDER BY query_start;"

# 3. Terminate idle connections
psql -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'payment_gateway' AND state = 'idle' AND query_start < now() - interval '10 minutes';"

# 4. Increase pool size (temporary)
# Update application.yml: spring.datasource.hikari.maximum-pool-size
```

### Slow Queries

**Symptoms:**
- High latency on transaction queries
- Database CPU high

**Solutions:**
```bash
# 1. Find slow queries
psql -c "SELECT query, calls, mean_time, total_time FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 10;"

# 2. Check for missing indexes
psql -c "EXPLAIN ANALYZE SELECT * FROM payments WHERE merchant_id = 'xxx' AND created_at > '2025-01-01';"

# 3. Add missing index if needed
psql -c "CREATE INDEX CONCURRENTLY idx_payments_merchant_created ON payments(merchant_id, created_at);"

# 4. Vacuum and analyze
psql -c "VACUUM ANALYZE payments;"
```

### Replication Lag

**Symptoms:**
- Stale data in queries
- Read replica behind primary

**Solutions:**
```bash
# 1. Check replication lag
psql -c "SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn FROM pg_stat_replication;"

# 2. If lag is high, check replica resources
# - CPU, memory, disk I/O

# 3. Consider routing reads to primary temporarily
```

## Performance Issues

### High Latency

**Symptoms:**
- P95 latency > 500ms
- Slow payment processing

**Debugging Steps:**

```bash
# 1. Check distributed traces
# Open Jaeger UI: http://localhost:16686
# Search for slow traces

# 2. Identify slow component
# Look at span durations in trace

# 3. Check service metrics
curl http://localhost:8446/actuator/prometheus | grep http_server_requests

# 4. Check database performance
psql -c "SELECT * FROM pg_stat_statements ORDER BY total_time DESC LIMIT 5;"

# 5. Check Redis latency
redis-cli --latency
```

### Memory Issues

**Symptoms:**
- OOMKilled pods
- High memory usage
- GC pauses

**Solutions:**
```bash
# 1. Check memory usage
kubectl top pods -n payment-gateway

# 2. Check JVM heap
curl http://localhost:8446/actuator/metrics/jvm.memory.used

# 3. Analyze heap dump (if available)
jmap -dump:format=b,file=heap.hprof <pid>
jhat heap.hprof

# 4. Increase memory limits
kubectl set resources deployment/authorization-service --limits=memory=4Gi -n payment-gateway
```

### High CPU

**Symptoms:**
- CPU throttling
- Slow response times
- High load average

**Solutions:**
```bash
# 1. Check CPU usage
kubectl top pods -n payment-gateway

# 2. Profile application
# Enable async-profiler or JFR

# 3. Check for hot spots
# Look for tight loops, inefficient algorithms

# 4. Scale horizontally
kubectl scale deployment/authorization-service --replicas=5 -n payment-gateway
```

## Integration Issues

### Webhook Delivery Failed

**Symptoms:**
- Webhooks not received
- Webhook status shows failures

**Solutions:**
```bash
# 1. Check webhook endpoint is accessible
curl -v https://your-webhook-endpoint.com/webhooks

# 2. Verify SSL certificate
openssl s_client -connect your-webhook-endpoint.com:443

# 3. Check webhook logs
# Look for delivery attempts and responses

# 4. Verify signature verification code
# Ensure using correct webhook secret
```

### 3DS Authentication Issues

**Symptoms:**
- 3DS challenge not appearing
- Authentication timeout
- "3DS failed" errors

**Solutions:**
```bash
# 1. Check 3DS service health
curl http://localhost:8448/actuator/health

# 2. Verify browser info is being sent
# Check request includes browserInfo object

# 3. Check ACS connectivity
# Verify network allows outbound to ACS URLs

# 4. Check session timeout
# Default is 5 minutes for 3DS challenge
```

### PSP Integration Issues

**Symptoms:**
- PSP requests failing
- Invalid credentials errors
- Unexpected responses

**Solutions:**
```bash
# 1. Verify PSP credentials
# Check API keys in Vault/config

# 2. Test PSP connectivity
curl -v https://api.stripe.com/v1/charges \
  -u sk_test_xxx:

# 3. Check PSP API version
# Ensure using supported API version

# 4. Review PSP logs
kubectl logs deployment/authorization-service -n payment-gateway | grep PSP
```

## Diagnostic Commands

### Quick Health Check

```bash
#!/bin/bash
echo "=== Service Health ==="
curl -s http://localhost:8446/actuator/health | jq .

echo "=== Database ==="
psql -c "SELECT count(*) as connections FROM pg_stat_activity WHERE datname = 'payment_gateway';"

echo "=== Redis ==="
redis-cli ping

echo "=== Kafka ==="
kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null && echo "OK" || echo "FAILED"

echo "=== Recent Errors ==="
kubectl logs deployment/authorization-service -n payment-gateway --since=5m | grep ERROR | tail -10
```

### Payment Debug

```bash
#!/bin/bash
PAYMENT_ID=$1

echo "=== Payment Details ==="
curl -s "http://localhost:8446/api/v1/payments/$PAYMENT_ID" \
  -H "X-API-Key: $API_KEY" | jq .

echo "=== Payment Events ==="
psql -c "SELECT event_type, event_status, created_at, error_message FROM payment_events WHERE payment_id = (SELECT id FROM payments WHERE payment_id = '$PAYMENT_ID') ORDER BY created_at;"

echo "=== Distributed Trace ==="
echo "Open Jaeger: http://localhost:16686/search?service=authorization-service&tags=%7B%22payment.id%22%3A%22$PAYMENT_ID%22%7D"
```

## Getting Help

If you can't resolve an issue:

1. **Gather Information:**
   - Error messages and codes
   - Trace ID from response
   - Timestamps of issue
   - Steps to reproduce

2. **Check Status Page:**
   - https://status.paymentgateway.example.com

3. **Contact Support:**
   - Email: support@paymentgateway.example.com
   - Emergency: +1-800-PAY-HELP

4. **Escalation Path:**
   - L1: General support (response: 4 hours)
   - L2: Technical support (response: 2 hours)
   - L3: Engineering (response: 1 hour)
   - P1: Critical incident (response: 15 minutes)
