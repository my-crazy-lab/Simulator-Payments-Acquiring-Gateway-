# Authorization Service

## Overview

The Authorization Service is the core payment processing component of the Payment Acquiring Gateway. It orchestrates the complete payment lifecycle from authorization through settlement, coordinating with multiple internal services (Tokenization, Fraud Detection, 3D Secure) and external PSPs.

## Features

- **REST API Endpoints**: Complete payment processing API
  - `POST /api/v1/payments` - Create and authorize payment
  - `GET /api/v1/payments/{id}` - Query payment status
  - `POST /api/v1/payments/{id}/capture` - Capture authorized payment
  - `POST /api/v1/payments/{id}/void` - Void authorized payment

- **Service Orchestration**: Coordinates payment flow across services
  1. Tokenization Service (PAN tokenization)
  2. Fraud Detection Service (risk scoring)
  3. 3D Secure Service (authentication if required)
  4. PSP (payment authorization)

- **Distributed Tracing**: OpenTelemetry integration for end-to-end observability
- **Metrics Collection**: Prometheus metrics for monitoring
- **Database**: PostgreSQL with JPA/Hibernate
- **Caching**: Redis for performance optimization

## Architecture

```
Client Request
     ↓
REST Controller (PaymentController)
     ↓
Service Layer (PaymentService)
     ↓
┌────────────────────────────────────┐
│  Service Orchestration Sequence:   │
│  1. Tokenization (gRPC)            │
│  2. Fraud Detection (gRPC)         │
│  3. 3D Secure (gRPC, if needed)    │
│  4. PSP Authorization (HTTP)       │
└────────────────────────────────────┘
     ↓
Database (PostgreSQL)
     ↓
Event Publishing (Kafka)
```

## Configuration

Key configuration in `application.yml`:

```yaml
server:
  port: 8446

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_gateway
  data:
    redis:
      host: localhost
      port: 6379

grpc:
  client:
    tokenization:
      address: static://localhost:8445
    fraud-detection:
      address: static://localhost:8447
    three-ds:
      address: static://localhost:8448
```

## Testing

### Property-Based Tests

The service includes comprehensive property-based tests using jqwik:

1. **PAN Never Stored Raw** (`PANNeverStoredRawPropertyTest`)
   - Validates: Requirements 1.1, 1.3
   - Verifies that raw PAN data is never stored in the database
   - Tests 100 random card numbers to ensure tokenization occurs

2. **Service Orchestration Sequence** (`ServiceOrchestrationSequencePropertyTest`)
   - Validates: Requirements 16.1
   - Verifies correct service call sequence
   - Tests 100 random payment requests

3. **Distributed Trace Propagation** (`DistributedTracePropagationPropertyTest`)
   - Validates: Requirements 10.1, 10.2
   - Verifies trace ID propagation across service boundaries
   - Tests 100 random requests for trace consistency

### Unit Tests

REST API endpoint tests (`PaymentControllerTest`):
- Request validation (missing fields, invalid formats)
- Error responses (400, 404, 500)
- Success scenarios for all endpoints
- Payment state transitions (authorize, capture, void)

## Running Tests

```bash
# Run all tests
mvn test

# Run only property tests
mvn test -Dtest="*PropertyTest"

# Run only unit tests
mvn test -Dtest="*ControllerTest"
```

## Building

```bash
# Build the service
mvn clean package

# Run the service
java -jar target/authorization-service-1.0.0-SNAPSHOT.jar
```

## API Examples

### Create Payment

```bash
curl -X POST http://localhost:8446/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Merchant-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "cardNumber": "4532015112830366",
    "expiryMonth": 12,
    "expiryYear": 2025,
    "cvv": "123",
    "amount": 100.00,
    "currency": "USD",
    "description": "Test payment"
  }'
```

### Get Payment

```bash
curl http://localhost:8446/api/v1/payments/pay_abc123
```

### Capture Payment

```bash
curl -X POST http://localhost:8446/api/v1/payments/pay_abc123/capture
```

### Void Payment

```bash
curl -X POST http://localhost:8446/api/v1/payments/pay_abc123/void
```

## Metrics

Prometheus metrics available at `/actuator/prometheus`:

- `payments.processed` - Total payments processed
- `payments.processing.time` - Payment processing duration
- Standard JVM and Spring Boot metrics

## Tracing

Distributed traces exported to OTLP endpoint (default: `http://localhost:4317`).

Each payment request creates a trace with spans for:
- Payment processing
- Tokenization
- Fraud detection
- 3D Secure authentication
- PSP authorization

## Dependencies

- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL Driver
- Redis (Lettuce)
- gRPC
- OpenTelemetry
- Micrometer (Prometheus)
- jqwik (Property-based testing)
- Testcontainers

## Implementation Status

✅ Core service implementation
✅ REST API endpoints
✅ Database entities and repositories
✅ OpenTelemetry distributed tracing
✅ Prometheus metrics
✅ Property-based tests (3 properties)
✅ Unit tests for REST API

## Next Steps

- Implement actual gRPC clients for Tokenization, Fraud Detection, and 3D Secure services
- Add Kafka event publishing
- Implement idempotency management
- Add rate limiting
- Implement authentication/authorization middleware
- Add comprehensive error handling and retry logic
