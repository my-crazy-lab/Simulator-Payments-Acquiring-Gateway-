# Payment Gateway Load Testing

This directory contains load testing scripts and configurations for the Payment Acquiring Gateway.

## Requirements

The load tests validate the following SLA requirements:
- **Requirement 27.1**: 99.99% uptime for payment authorization
- **Requirement 27.2**: <500ms p95 response time
- **Requirement 27.3**: 10,000 TPS throughput

## Prerequisites

1. Install k6 load testing tool:
   ```bash
   # macOS
   brew install k6
   
   # Linux (Debian/Ubuntu)
   sudo gpg -k
   sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
   echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update
   sudo apt-get install k6
   
   # Docker
   docker pull grafana/k6
   ```

2. Ensure the Payment Gateway services are running:
   ```bash
   docker-compose up -d
   ```

## Running Load Tests

### Quick Smoke Test
```bash
k6 run --vus 10 --duration 1m load-tests/k6-payment-load-test.js
```

### Standard Load Test (10K TPS target)
```bash
k6 run load-tests/k6-payment-load-test.js
```

### Stress Test (Find breaking point)
```bash
k6 run --vus 500 --duration 30m load-tests/k6-payment-load-test.js
```

### Soak Test (24-hour endurance)
```bash
k6 run --vus 200 --duration 24h load-tests/k6-payment-load-test.js
```

### With Custom Configuration
```bash
BASE_URL=http://your-gateway:8446 API_KEY=your_api_key k6 run load-tests/k6-payment-load-test.js
```

## Test Scenarios

### 1. Constant Load Test
- Maintains 10,000 requests per second
- Duration: 5 minutes
- Purpose: Verify sustained throughput capability

### 2. Ramping Load Test
- Gradually increases load from 100 to 10,000 TPS
- Duration: 25 minutes total
- Purpose: Identify performance degradation points

## Metrics Collected

| Metric | Description | SLA Threshold |
|--------|-------------|---------------|
| `http_req_duration` | Overall request latency | p95 < 500ms |
| `http_req_failed` | Request failure rate | < 1% |
| `payment_success_rate` | Payment creation success | > 99% |
| `payment_latency` | Payment-specific latency | p95 < 500ms |
| `capture_latency` | Capture operation latency | p95 < 500ms |
| `query_latency` | Query operation latency | p95 < 200ms |
| `errors` | Total error count | < 100 |

## Results

Test results are saved to `load-tests/results/summary.json` after each run.

### Interpreting Results

```
=== Payment Gateway Load Test Summary ===

Total Requests: 1000000
Success Rate: 99.95%

Latency (ms):
  p50: 45.23
  p95: 312.45
  p99: 487.89

Payment Metrics:
  Success Rate: 99.92%
  p95 Latency: 298.34ms
  Errors: 8
```

## Performance Tuning

If tests fail to meet SLA thresholds, consider:

1. **Database Optimization**
   - Increase connection pool size
   - Add missing indexes
   - Enable query caching

2. **Application Tuning**
   - Increase thread pool sizes
   - Enable response compression
   - Optimize JVM heap settings

3. **Infrastructure Scaling**
   - Add more service instances
   - Increase database resources
   - Scale Redis cluster

## Grafana Dashboard

Import the provided Grafana dashboard for real-time monitoring during load tests:
```
config/grafana/dashboards/payment-gateway-dashboard.json
```

## Troubleshooting

### High Error Rate
- Check service logs: `docker-compose logs authorization-service`
- Verify database connections: `docker-compose exec postgres pg_stat_activity`
- Check Redis connectivity: `docker-compose exec redis redis-cli ping`

### High Latency
- Monitor database query times
- Check for connection pool exhaustion
- Verify network latency between services

### Memory Issues
- Increase JVM heap: `-Xmx4g -Xms4g`
- Enable GC logging for analysis
- Check for memory leaks with profiler
