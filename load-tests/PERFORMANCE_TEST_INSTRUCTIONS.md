# Performance Test Execution Instructions

## Prerequisites

Before running performance tests, ensure the following services are running:

1. **PostgreSQL Database**
   ```bash
   docker-compose up -d postgres
   ```

2. **Redis Cache**
   ```bash
   docker-compose up -d redis
   ```

3. **Kafka (optional, for event publishing)**
   ```bash
   docker-compose up -d kafka
   ```

4. **Authorization Service**
   ```bash
   cd authorization-service
   mvn spring-boot:run
   ```

5. **Install k6 Load Testing Tool**
   ```bash
   # macOS
   brew install k6
   
   # Linux
   sudo apt-get install k6
   
   # Or use Docker
   docker pull grafana/k6
   ```

## Running Performance Tests

### Task 26.1: Load Test at 10K TPS

```bash
cd load-tests
make load
```

Or manually:
```bash
k6 run --env BASE_URL=http://localhost:8446 k6-payment-load-test.js
```

### Task 26.1: Stress Test to Find Limits

```bash
cd load-tests
make stress
```

### Task 26.1: Soak Test for 24 Hours

```bash
cd load-tests
make soak
```

## Expected Results

### SLA Thresholds (Requirements 27.1, 27.2, 27.3)

| Metric | Target | Threshold |
|--------|--------|-----------|
| Availability | 99.99% | Error rate < 0.01% |
| p95 Latency | < 500ms | http_req_duration p95 < 500ms |
| Throughput | 10,000 TPS | Sustained for 5+ minutes |

### Interpreting Results

After running tests, check `load-tests/results/summary.json` for detailed metrics.

Key metrics to monitor:
- `http_req_duration`: Request latency percentiles
- `http_req_failed`: Error rate
- `payment_success_rate`: Payment creation success rate
- `payment_latency`: Payment-specific latency

## Troubleshooting

### High Latency
1. Check database connection pool utilization
2. Verify Redis cache hit rate
3. Monitor JVM heap usage

### High Error Rate
1. Check service logs for exceptions
2. Verify database connections are not exhausted
3. Check for circuit breaker activations

### Memory Issues
1. Increase JVM heap: `-Xmx4g -Xms4g`
2. Enable GC logging: `-Xlog:gc*`
3. Profile with async-profiler or JFR

## Performance Optimizations Applied

The following optimizations have been implemented:

1. **Database Connection Pooling** (Requirement 20.1)
   - HikariCP with 50 max connections
   - Prepared statement caching
   - Batch insert optimization

2. **Query Optimization** (Requirement 20.2, 20.4)
   - Additional indexes for payment workflows
   - Query hints for fetch size optimization
   - Batch update operations

3. **Caching Strategy** (Requirement 15.4, 23.4)
   - Redis caching with configurable TTLs
   - Cache-aside pattern for payments
   - Exchange rate caching

4. **Async Processing**
   - Dedicated thread pools for PSP communication
   - Async event publishing to Kafka
   - Non-blocking webhook delivery
