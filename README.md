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

## Core Features

### Functinal

- **PCI DSS Compliance**: Secure card data handling with tokenization
- **Card Tokenization**: PAN tokenization with format-preserving encryption
- **Fraud Detection**: Real-time ML-based fraud scoring
- **3D Secure Flow**: Complete 3DS 2.0 authentication workflow
- **Multi-PSP Routing**: Intelligent routing to multiple payment providers
- **Settlement Processing**: Automated settlement with reconciliation
- **Dispute Management**: Chargeback and dispute handling workflow
- **Retry Logic**: Exponential backoff with circuit breaker patterns
- **HSM Integration**: Hardware Security Module simulation

### Non Functional

- **Secure Enclaves**: Isolated PCI-compliant tokenization service
- **Event-Driven Architecture**: Kafka-based async processing
- **Caching Layer**: Redis for session management and rate limiting
- **Database Encryption**: Field-level encryption for sensitive data
- **API Security**: OAuth 2.0, JWT tokens, and API key management
- **Monitoring**: Real-time fraud alerts and transaction monitoring

## External Integrations

### Payment Service Providers (PSPs)

- **Stripe**: Credit card processing
- **Adyen**: Global payment processing
- **Braintree**: PayPal-owned processor
- **Square**: Point-of-sale integration
- **Worldpay**: Enterprise payment processing

### Card Schemes

- **Visa**: Visa network integration
- **Mastercard**: Mastercard network
- **American Express**: Amex direct integration
- **Discover**: Discover network

### Acquiring Banks

- **Chase Paymentech**: Bank acquiring services
- **First Data**: Payment processing
- **TSYS**: Transaction processing
- **Elavon**: Merchant services

## Compliance Certifications

- **PCI DSS Level 1**: Highest level of PCI compliance
- **SOC 2 Type II**: Security and availability controls
- **ISO 27001**: Information security management
- **GDPR**: Data privacy compliance
- **PSD2**: European payment services directive

## Security & Compliance

### PCI DSS Compliance

- **Scope Minimization**: Tokenization reduces PCI scope
- **Data Encryption**: AES-256 encryption for card data
- **Access Controls**: Role-based access with MFA
- **Network Security**: TLS 1.3, network segmentation
- **Audit Logging**: Comprehensive audit trail
- **Vulnerability Management**: Regular security scans

### Fraud Prevention

- **Real-time Scoring**: ML-based fraud detection
- **Velocity Checks**: Transaction frequency limits
- **Geolocation Analysis**: Location-based risk assessment
- **Device Fingerprinting**: Device-based fraud detection
- **Behavioral Analysis**: User behavior pattern analysis
- **Blacklist Management**: Dynamic blacklist updates

### 3D Secure 2.0

- **Frictionless Flow**: Risk-based authentication
- **Challenge Flow**: Step-up authentication when needed
- **Browser Integration**: Seamless browser experience
- **Mobile SDK**: Native mobile app integration
- **Issuer Integration**: Real-time issuer communication
