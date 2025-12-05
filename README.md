# Payment Acquiring Gateway

A PCI DSS Level 1 compliant payment acquiring gateway built with microservices architecture.

## Architecture

The system consists of multiple services written in different languages:

### Java Services (Spring Boot)
- **Authorization Service** (Port 8446) - Main payment processing API
- **Fraud Detection Service** (Port 8447) - Real-time fraud scoring
- **3D Secure Service** (Port 8448) - 3DS authentication
- **Settlement Service** (Port 8449) - Daily settlement processing

### Go Services
- **Tokenization Service** (Port 8445) - PAN tokenization [PCI SCOPE]
- **HSM Simulator** (Port 8444) - Cryptographic operations [PCI SCOPE]

### Rust Services
- **Retry Engine** (Port 8450) - Intelligent retry with circuit breakers

### Shared Library
- Common utilities for logging, tracing, and metrics
- Property-based testing infrastructure

## Infrastructure

### Required Services
- **PostgreSQL 15** - Primary database
- **Redis 7** - Caching and session management
- **Kafka + Schema Registry** - Event streaming
- **Vault** - Secrets management
- **Jaeger** - Distributed tracing
- **Prometheus** - Metrics collection
- **Grafana** - Metrics visualization

## Getting Started

### Prerequisites
- Java 17+
- Go 1.21+
- Rust 1.70+
- Docker & Docker Compose
- Maven 3.8+

### Start Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d

# Verify services are healthy
docker-compose ps
```

### Build Services

```bash
# Build Java services
mvn clean install

# Build Go services
cd tokenization-service && go build
cd ../hsm-simulator && go build

# Build Rust service
cd retry-engine && cargo build
```

### Run Tests

```bash
# Run all Java tests including property-based tests
mvn test

# Run Go tests
cd tokenization-service && go test ./...
cd ../hsm-simulator && go test ./...

# Run Rust tests
cd retry-engine && cargo test
```

## Project Structure

```
.
├── authorization-service/       # Java - Main payment API
├── fraud-detection-service/     # Java - Fraud detection
├── 3ds-service/                 # Java - 3D Secure
├── settlement-service/          # Java - Settlement processing
├── tokenization-service/        # Go - PAN tokenization [PCI]
├── hsm-simulator/               # Go - HSM operations [PCI]
├── retry-engine/                # Rust - Retry logic
├── shared-lib/                  # Java - Common utilities
├── config/                      # Configuration files
├── schema.sql                   # Database schema
├── docker-compose.yml           # Infrastructure setup
└── pom.xml                      # Maven parent POM
```

## PCI DSS Compliance

### PCI Scoped Services
- Tokenization Service
- HSM Simulator
- Encrypted database fields

### Security Features
- TLS 1.3 for all external communication
- mTLS for internal service communication
- Field-level encryption (AES-256-GCM)
- PAN tokenization with format-preserving encryption
- Audit logging with sensitive data redaction
- Key rotation with backward compatibility

## Testing Strategy

### Property-Based Testing
- 100+ iterations per property
- 40 correctness properties covering all requirements
- Smart generators for valid input domains
- Automatic shrinking to minimal failing cases

### Unit Testing
- Specific edge cases and error conditions
- Mock external dependencies
- Integration points validation

### Integration Testing
- End-to-end payment flows
- Testcontainers for infrastructure
- Kafka event flows
- Database transactions

## Monitoring

### Metrics (Prometheus)
- Access at: http://localhost:9090
- Service-specific metrics endpoints

### Tracing (Jaeger)
- Access at: http://localhost:16686
- Distributed trace visualization

### Dashboards (Grafana)
- Access at: http://localhost:3000
- Default credentials: admin/admin

## API Documentation

### Authorization Service
- Base URL: http://localhost:8446
- Swagger UI: http://localhost:8446/swagger-ui.html

### Key Endpoints
- `POST /api/v1/payments` - Process payment
- `GET /api/v1/payments/{id}` - Query payment
- `POST /api/v1/payments/{id}/capture` - Capture authorization
- `POST /api/v1/refunds` - Process refund

## Development

### Environment Variables
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/payment_gateway
DATABASE_USER=payments_user
DATABASE_PASSWORD=payments_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis_pass

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9093
SCHEMA_REGISTRY_URL=http://localhost:8081

# Vault
VAULT_ADDR=http://localhost:8200
VAULT_TOKEN=root-token

# Tracing
JAEGER_ENDPOINT=http://localhost:14268/api/traces
```

## License

Proprietary - All rights reserved
