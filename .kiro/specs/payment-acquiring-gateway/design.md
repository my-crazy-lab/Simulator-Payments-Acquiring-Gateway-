# Design Document: Payment Acquiring Gateway

## Overview

The Payment Acquiring Gateway is a distributed, microservices-based system designed to process card payment transactions with PCI DSS Level 1 compliance. The system orchestrates the complete payment lifecycle from authorization through settlement, integrating with multiple Payment Service Providers (PSPs), acquiring banks, and card schemes.

### Key Design Principles

1. **Security First**: PCI DSS compliance is embedded at every layer with encryption, tokenization, and strict access controls
2. **High Availability**: Distributed architecture with fault tolerance, circuit breakers, and graceful degradation
3. **Event-Driven**: Asynchronous processing using Kafka for scalability and eventual consistency
4. **Service Isolation**: Clear boundaries between PCI-scoped and non-scoped services
5. **Observability**: Comprehensive tracing, metrics, and logging across all services

### System Characteristics

- **Throughput**: 10,000+ transactions per second
- **Latency**: <500ms p95 for authorization requests
- **Availability**: 99.99% uptime SLA
- **Data Retention**: 7 years for audit compliance
- **Geographic Distribution**: Multi-region deployment capability

## Architecture

### High-Level Architecture

The system follows a microservices architecture with clear separation between PCI-scoped and non-scoped components:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Layer                              │
│  Web/Mobile Apps, POS Terminals → API Gateway (TLS 1.3)        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   Authorization Service (Java)                   │
│  • REST API endpoints                                            │
│  • Transaction orchestration                                     │
│  • PSP routing logic                                            │
│  • gRPC client for internal services                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
┌──────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ Tokenization │    │ Fraud Detection  │    │  3D Secure      │
│ Service (Go) │    │ Service (Java)   │    │  Service (Java) │
│ [PCI SCOPE]  │    │                  │    │                 │
└──────────────┘    └──────────────────┘    └─────────────────┘
        ↓                     ↓                     ↓
┌──────────────┐    ┌──────────────────┐    ┌─────────────────┐
│ HSM Simulator│    │  Kafka Events    │    │  External ACS   │
│    (Go)      │    │                  │    │                 │
│ [PCI SCOPE]  │    └──────────────────┘    └─────────────────┘
└──────────────┘              ↓
        ↓              ┌──────────────────┐
┌──────────────┐      │   Settlement     │
│    Vault     │      │  Service (Java)  │
│  (Secrets)   │      │                  │
│ [PCI SCOPE]  │      └──────────────────┘
└──────────────┘              ↓
                      ┌──────────────────┐
                      │  Retry Engine    │
                      │    (Rust)        │
                      └──────────────────┘
                              ↓
        ┌─────────────────────┼─────────────────────┐
        ↓                     ↓                     ↓
┌──────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  PSP 1       │    │  PSP 2           │    │  Acquiring Bank │
│  (Stripe)    │    │  (Adyen)         │    │                 │
└──────────────┘    └──────────────────┘    └─────────────────┘
```

### Service Boundaries

**PCI DSS Scope (Isolated Network Segment):**
- Tokenization Service (Port 8445)
- HSM Simulator (Port 8444)
- Vault (Secrets Management)
- Encrypted database fields

**Non-PCI Scope:**
- Authorization Service (Port 8446)
- Fraud Detection Service (Port 8447)
- 3D Secure Service (Port 8448)
- Settlement Service (Port 8449)
- Retry Engine (Port 8450)

### Communication Patterns

1. **Synchronous (gRPC)**: Internal service-to-service calls requiring immediate response
   - Authorization → Tokenization
   - Authorization → Fraud Detection
   - Authorization → 3D Secure

2. **Asynchronous (Kafka)**: Event-driven workflows for eventual consistency
   - Payment state changes
   - Settlement batch processing
   - Fraud alert notifications
   - Webhook delivery

3. **REST API**: External merchant-facing endpoints
   - Payment processing
   - Transaction queries
   - Refund requests

## Components and Interfaces

### 1. Authorization Service (Java, Port 8446)

**Responsibilities:**
- Primary payment processing API
- Transaction orchestration across services
- PSP routing and failover
- Idempotency management
- Rate limiting enforcement

**Key Interfaces:**

```java
// REST API
POST   /api/v1/payments
GET    /api/v1/payments/{id}
POST   /api/v1/payments/{id}/capture
POST   /api/v1/payments/{id}/void
POST   /api/v1/refunds
GET    /api/v1/transactions/{id}

// gRPC Internal
service AuthorizationService {
  rpc ProcessPayment(PaymentRequest) returns (PaymentResponse);
  rpc ValidateTransaction(ValidationRequest) returns (ValidationResponse);
}
```

**Dependencies:**
- Tokenization Service (gRPC)
- Fraud Detection Service (gRPC)
- 3D Secure Service (gRPC)
- PostgreSQL (transaction data)
- Redis (caching, rate limiting, idempotency)
- Kafka (event publishing)

### 2. Tokenization Service (Go, Port 8445) [PCI SCOPE]

**Responsibilities:**
- PAN tokenization with format-preserving encryption
- Token lifecycle management
- Secure key retrieval from HSM
- Detokenization for authorized services

**Key Interfaces:**

```go
// gRPC API
service TokenizationService {
  rpc TokenizeCard(TokenizeRequest) returns (TokenizeResponse);
  rpc DetokenizeCard(DetokenizeRequest) returns (DetokenizeResponse);
  rpc ValidateToken(ValidateRequest) returns (ValidateResponse);
}

// Internal
type TokenizeRequest struct {
  PAN          string
  ExpiryMonth  int
  ExpiryYear   int
  CVV          string
}

type TokenizeResponse struct {
  Token        string
  LastFour     string
  CardBrand    string
  ExpiresAt    time.Time
}
```

**Dependencies:**
- HSM Simulator (cryptographic operations)
- Vault (configuration, key metadata)
- PostgreSQL (encrypted token storage)

### 3. HSM Simulator (Go, Port 8444) [PCI SCOPE]

**Responsibilities:**
- Cryptographic key generation and storage
- Encryption/decryption operations
- Key rotation management
- Audit logging for key access

**Key Interfaces:**

```go
// gRPC API
service HSMService {
  rpc GenerateKey(GenerateKeyRequest) returns (GenerateKeyResponse);
  rpc Encrypt(EncryptRequest) returns (EncryptResponse);
  rpc Decrypt(DecryptRequest) returns (DecryptResponse);
  rpc RotateKey(RotateKeyRequest) returns (RotateKeyResponse);
}

// Encryption using AES-256-GCM
type EncryptRequest struct {
  KeyID     string
  Plaintext []byte
  AAD       []byte  // Additional authenticated data
}
```

**Dependencies:**
- Secure key storage (encrypted filesystem or vault)
- Audit log database

### 4. Fraud Detection Service (Java, Port 8447)

**Responsibilities:**
- Real-time ML-based fraud scoring
- Velocity checks (transaction frequency)
- Geolocation risk assessment
- Rule engine evaluation
- Blacklist/whitelist management

**Key Interfaces:**

```java
// gRPC API
service FraudDetectionService {
  rpc EvaluateTransaction(FraudRequest) returns (FraudResponse);
  rpc UpdateRules(RuleUpdateRequest) returns (RuleUpdateResponse);
}

// Internal
class FraudRequest {
  String transactionId;
  BigDecimal amount;
  String currency;
  String cardToken;
  String ipAddress;
  String deviceFingerprint;
  Address billingAddress;
  Map<String, Object> metadata;
}

class FraudResponse {
  double fraudScore;  // 0.0 to 1.0
  FraudStatus status; // CLEAN, REVIEW, BLOCK
  List<String> triggeredRules;
  boolean require3DS;
}
```

**Dependencies:**
- PostgreSQL (fraud rules, alerts)
- Redis (velocity tracking, blacklists)
- ML model service (fraud scoring)
- Kafka (fraud alert events)

### 5. 3D Secure Service (Java, Port 8448)

**Responsibilities:**
- 3DS 2.0 authentication workflow
- Browser and mobile SDK integration
- ACS communication
- Risk-based authentication decisions

**Key Interfaces:**

```java
// gRPC API
service ThreeDSecureService {
  rpc InitiateAuth(ThreeDSRequest) returns (ThreeDSResponse);
  rpc CompleteAuth(ThreeDSCompleteRequest) returns (ThreeDSCompleteResponse);
}

// REST API (for browser redirects)
POST /api/v1/3ds/initiate
POST /api/v1/3ds/callback

// Internal
class ThreeDSRequest {
  String transactionId;
  BigDecimal amount;
  String currency;
  String cardToken;
  BrowserInfo browserInfo;
  String merchantReturnUrl;
}

class ThreeDSResponse {
  ThreeDSStatus status;  // FRICTIONLESS, CHALLENGE_REQUIRED
  String acsUrl;         // For challenge flow
  String transactionId;
  String cavv;           // Cardholder Authentication Verification Value
  String eci;            // Electronic Commerce Indicator
}
```

**Dependencies:**
- External ACS (issuer authentication server)
- PostgreSQL (3DS transaction state)
- Redis (session management)

### 6. Settlement Service (Java, Port 8449)

**Responsibilities:**
- Daily settlement batch processing
- Transaction reconciliation
- Settlement file generation
- Dispute and chargeback handling

**Key Interfaces:**

```java
// Internal API
service SettlementService {
  rpc CreateSettlementBatch(BatchRequest) returns (BatchResponse);
  rpc ReconcileTransactions(ReconcileRequest) returns (ReconcileResponse);
  rpc ProcessChargeback(ChargebackRequest) returns (ChargebackResponse);
}

// Scheduled Jobs
@Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
void processSettlementBatches();

@Scheduled(cron = "0 0 3 * * *")  // 3 AM daily
void reconcileWithAcquirer();
```

**Dependencies:**
- PostgreSQL (settlement batches, transactions)
- Kafka (settlement events)
- Acquirer APIs (settlement submission)
- SFTP (settlement file transfer)

### 7. Retry Engine (Rust, Port 8450)

**Responsibilities:**
- Intelligent retry with exponential backoff
- Circuit breaker pattern implementation
- Multi-PSP failover routing
- Dead letter queue processing

**Key Interfaces:**

```rust
// gRPC API
service RetryEngine {
  rpc ScheduleRetry(RetryRequest) returns (RetryResponse);
  rpc GetCircuitStatus(CircuitRequest) returns (CircuitResponse);
}

// Internal
struct RetryPolicy {
  max_attempts: u32,
  initial_delay_ms: u64,
  max_delay_ms: u64,
  backoff_multiplier: f64,
  jitter: bool,
}

struct CircuitBreaker {
  failure_threshold: u32,
  success_threshold: u32,
  timeout_duration: Duration,
  state: CircuitState,  // CLOSED, OPEN, HALF_OPEN
}
```

**Dependencies:**
- Redis (circuit breaker state)
- Kafka (retry events, DLQ)
- PostgreSQL (retry history)

### 8. API Gateway

**Responsibilities:**
- TLS termination
- Authentication and authorization
- Rate limiting (per merchant)
- Request validation
- Response transformation

**Key Features:**
- OAuth 2.0 / JWT token validation
- API key authentication
- CORS policy enforcement
- Request/response logging
- Security headers injection

## Data Models

### Core Entities

#### Payment Transaction

```sql
CREATE TABLE payments (
  id UUID PRIMARY KEY,
  payment_id VARCHAR(100) UNIQUE,
  merchant_id UUID REFERENCES merchants(id),
  
  -- Financial
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  
  -- Card (tokenized)
  card_token_id UUID REFERENCES card_tokens(id),
  card_last_four VARCHAR(4),
  card_brand card_brand,
  
  -- Status
  status payment_status,
  transaction_type transaction_type,
  
  -- External references
  psp_transaction_id VARCHAR(100),
  acquirer_reference VARCHAR(100),
  
  -- Fraud
  fraud_score DECIMAL(3,2),
  fraud_status fraud_status,
  
  -- 3DS
  three_ds_status three_ds_status,
  three_ds_cavv TEXT,
  three_ds_eci VARCHAR(2),
  
  -- Timestamps
  created_at TIMESTAMP WITH TIME ZONE,
  authorized_at TIMESTAMP WITH TIME ZONE,
  captured_at TIMESTAMP WITH TIME ZONE,
  settled_at TIMESTAMP WITH TIME ZONE
);
```

#### Card Token (PCI Scope)

```sql
CREATE TABLE card_tokens (
  id UUID PRIMARY KEY,
  token VARCHAR(255) UNIQUE,
  
  -- Encrypted (AES-256-GCM)
  encrypted_pan TEXT NOT NULL,
  pan_hash VARCHAR(64) NOT NULL,  -- SHA-256 for lookups
  encrypted_expiry TEXT NOT NULL,
  
  -- Metadata
  card_brand card_brand,
  last_four VARCHAR(4),
  issuer_country VARCHAR(2),
  card_type VARCHAR(20),
  
  -- Security
  tokenization_method VARCHAR(50),
  key_version INTEGER,
  is_active BOOLEAN,
  expires_at TIMESTAMP WITH TIME ZONE
);
```

#### Merchant

```sql
CREATE TABLE merchants (
  id UUID PRIMARY KEY,
  merchant_id VARCHAR(50) UNIQUE,
  merchant_name VARCHAR(255),
  
  -- Business
  mcc VARCHAR(4),  -- Merchant Category Code
  country_code VARCHAR(2),
  currency VARCHAR(3),
  risk_level VARCHAR(20),
  
  -- PCI
  pci_compliance_level VARCHAR(10),
  last_pci_scan DATE,
  
  -- API (hashed)
  api_key_hash VARCHAR(255),
  webhook_url TEXT,
  webhook_secret_hash VARCHAR(255),
  
  is_active BOOLEAN
);
```

#### Settlement Batch

```sql
CREATE TABLE settlement_batches (
  id UUID PRIMARY KEY,
  batch_id VARCHAR(100) UNIQUE,
  merchant_id UUID REFERENCES merchants(id),
  
  settlement_date DATE,
  currency VARCHAR(3),
  total_amount DECIMAL(12,2),
  transaction_count INTEGER,
  
  status settlement_status,
  bank_reference VARCHAR(100),
  
  created_at TIMESTAMP WITH TIME ZONE,
  processed_at TIMESTAMP WITH TIME ZONE
);
```

### Event Schema (Kafka)

#### Payment Event

```json
{
  "event_id": "evt_abc123",
  "event_type": "PAYMENT_AUTHORIZED",
  "timestamp": "2025-12-05T10:30:00Z",
  "correlation_id": "uuid",
  "trace_id": "uuid",
  
  "payload": {
    "payment_id": "pay_xyz789",
    "merchant_id": "MERCHANT_001",
    "amount": "10000",
    "currency": "USD",
    "status": "AUTHORIZED",
    "psp_transaction_id": "ch_stripe_123",
    "fraud_score": 0.15,
    "three_ds_status": "AUTHENTICATED"
  }
}
```

### Data Flow

1. **Authorization Flow:**
   ```
   Merchant → API Gateway → Authorization Service
   → Tokenization Service (tokenize PAN)
   → Fraud Detection Service (score transaction)
   → 3D Secure Service (if required)
   → PSP (authorization request)
   → Kafka (publish PAYMENT_AUTHORIZED event)
   → Response to Merchant
   ```

2. **Settlement Flow:**
   ```
   Scheduled Job → Settlement Service
   → Query captured transactions
   → Create settlement batch
   → Generate settlement file
   → Submit to Acquirer
   → Kafka (publish SETTLEMENT_PROCESSED event)
   → Reconciliation
   ```

### Data Encryption Strategy

**Field-Level Encryption (PCI Scope):**
- PAN: AES-256-GCM with HSM-managed keys
- CVV: Never stored (used only for authorization)
- Expiry: AES-256-GCM encryption
- Key rotation: Quarterly with backward compatibility

**At-Rest Encryption:**
- PostgreSQL: Transparent Data Encryption (TDE)
- Backups: Encrypted with separate keys
- Logs: Sensitive fields redacted

**In-Transit Encryption:**
- TLS 1.3 for all external communication
- mTLS for internal service-to-service
- Certificate rotation: 90-day validity


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Acceptance Criteria Testing Prework

1.1 WHEN a merchant submits a payment request with valid card details THEN the Payment Gateway SHALL tokenize the PAN before processing
Thoughts: This is a rule that should apply to all payment requests with card details. We can generate random payment requests with valid card data, submit them, and verify that the PAN is tokenized before any processing occurs. This is testable across all inputs.
Testable: yes - property

1.2 WHEN the Payment Gateway processes a payment THEN the Payment Gateway SHALL encrypt all sensitive card data using AES-256-GCM
Thoughts: This applies to all payments. We can generate random payment data, process it, and verify that all sensitive fields are encrypted with the correct algorithm.
Testable: yes - property

1.3 WHEN the Payment Gateway stores transaction data THEN the Payment Gateway SHALL maintain field-level encryption for PAN, CVV, and cardholder data
Thoughts: This is about data at rest. For any transaction stored, we should verify the sensitive fields are encrypted. This is a universal property.
Testable: yes - property

1.4 WHEN the Payment Gateway handles card data THEN the Payment Gateway SHALL isolate PCI-scoped services in a separate security boundary
Thoughts: This is an architectural requirement about network segmentation. Not directly testable as a property.
Testable: no

1.5 WHEN the Payment Gateway communicates with external services THEN the Payment Gateway SHALL use TLS 1.3 for all network connections
Thoughts: For any external communication, we can verify the TLS version. This is testable across all external calls.
Testable: yes - property

2.1 WHEN the Tokenization Service receives a PAN THEN the Tokenization Service SHALL generate a unique token using format-preserving encryption
Thoughts: For any PAN input, we should get a unique token. We can test uniqueness across many random PANs.
Testable: yes - property

2.2 WHEN the Tokenization Service creates a token THEN the Tokenization Service SHALL store the encrypted PAN-to-token mapping in the Vault
Thoughts: For any tokenization operation, we can verify the mapping is stored. This is testable across all tokenization calls.
Testable: yes - property

2.3 WHEN the Tokenization Service encrypts a PAN THEN the Tokenization Service SHALL use cryptographic keys managed by the HSM
Thoughts: For any encryption operation, we can verify it uses HSM keys. This is testable across all encryptions.
Testable: yes - property

2.4 WHEN a service requests detokenization with a valid token THEN the Tokenization Service SHALL return the original PAN
Thoughts: This is a round-trip property. For any PAN, tokenize then detokenize should return the original value.
Testable: yes - property

2.5 WHEN the Tokenization Service receives a detokenization request with an invalid token THEN the Tokenization Service SHALL reject the request and log the attempt
Thoughts: This tests error handling for invalid inputs. We can generate invalid tokens and verify rejection.
Testable: yes - property


3.1 WHEN the Authorization Service processes a payment THEN the Authorization Service SHALL select a PSP based on merchant configuration and routing rules
Thoughts: For any payment with merchant config, we can verify the correct PSP is selected according to rules. This is testable across different configurations.
Testable: yes - property

3.2 WHEN a PSP returns an error response THEN the Authorization Service SHALL attempt failover to an alternative PSP according to retry policies
Thoughts: For any PSP error, we should see failover behavior. This is testable across different error scenarios.
Testable: yes - property

3.3 WHEN the Authorization Service sends a request to a PSP THEN the Authorization Service SHALL include all required authentication credentials and transaction data
Thoughts: For any PSP request, we can verify all required fields are present. This is testable across all requests.
Testable: yes - property

3.4 WHEN the Authorization Service receives a response from a PSP THEN the Authorization Service SHALL parse the response and map it to the internal transaction status
Thoughts: For any PSP response, we should correctly map to internal status. This is testable across different response types.
Testable: yes - property

3.5 WHEN multiple PSPs are unavailable THEN the Authorization Service SHALL activate the circuit breaker and return an appropriate error to the merchant
Thoughts: This is testing circuit breaker behavior under specific failure conditions. This is an edge case.
Testable: edge-case

4.1 WHEN the Fraud Detection Service receives a transaction THEN the Fraud Detection Service SHALL calculate a fraud score using machine learning models
Thoughts: For any transaction, we should get a fraud score. We can verify the score is calculated and within valid range.
Testable: yes - property

4.2 WHEN the Fraud Detection Service evaluates a transaction THEN the Fraud Detection Service SHALL perform velocity checks against recent transaction history
Thoughts: For any transaction, velocity checks should be performed. We can verify this happens for all transactions.
Testable: yes - property

4.3 WHEN the Fraud Detection Service detects a high-risk transaction THEN the Fraud Detection Service SHALL flag the transaction and trigger additional authentication requirements
Thoughts: For any high-risk transaction (score above threshold), we should see flagging and 3DS requirement. This is testable.
Testable: yes - property

4.4 WHEN the Fraud Detection Service identifies suspicious patterns THEN the Fraud Detection Service SHALL create fraud alerts for manual review
Thoughts: For any suspicious pattern detection, an alert should be created. This is testable across pattern types.
Testable: yes - property

4.5 WHEN the Fraud Detection Service processes a transaction from a blacklisted source THEN the Fraud Detection Service SHALL reject the transaction immediately
Thoughts: For any blacklisted source, transaction should be rejected. This is testable across blacklist entries.
Testable: yes - property

5.1 WHEN a transaction requires 3D Secure authentication THEN the 3D Secure Service SHALL redirect the cardholder to the Issuer's ACS
Thoughts: For any transaction requiring 3DS, we should see redirect to ACS. This is testable across transactions.
Testable: yes - property

5.2 WHEN the 3D Secure Service initiates authentication THEN the 3D Secure Service SHALL send device and browser information to support risk-based authentication
Thoughts: For any 3DS initiation, device/browser info should be included. This is testable across all initiations.
Testable: yes - property

5.3 WHEN the ACS completes authentication THEN the 3D Secure Service SHALL receive and validate the authentication response
Thoughts: For any ACS response, we should validate it. This is testable across response types.
Testable: yes - property

5.4 WHEN authentication is successful THEN the 3D Secure Service SHALL include the authentication data in the authorization request to the PSP
Thoughts: For any successful auth, the data should be included in PSP request. This is testable.
Testable: yes - property

5.5 WHEN authentication fails or times out THEN the 3D Secure Service SHALL reject the transaction and notify the merchant
Thoughts: For any auth failure, transaction should be rejected. This is testable across failure scenarios.
Testable: yes - property


6.1 WHEN the Settlement Service runs a settlement batch THEN the Settlement Service SHALL aggregate all captured transactions since the last settlement
Thoughts: For any settlement run, all captured transactions should be aggregated. This is testable across different transaction sets.
Testable: yes - property

6.2 WHEN the Settlement Service creates a settlement file THEN the Settlement Service SHALL format the file according to the Acquirer's specifications
Thoughts: For any settlement file created, we can verify it matches the spec format. This is testable across different batches.
Testable: yes - property

6.3 WHEN the Settlement Service submits a settlement batch THEN the Settlement Service SHALL receive confirmation from the Acquirer
Thoughts: For any submission, we should get confirmation. This is testable but depends on external system.
Testable: yes - property

6.4 WHEN the Settlement Service performs reconciliation THEN the Settlement Service SHALL compare submitted transactions with Acquirer settlement reports
Thoughts: For any reconciliation, we should compare our records with acquirer data. This is testable.
Testable: yes - property

6.5 WHEN the Settlement Service detects discrepancies THEN the Settlement Service SHALL create reconciliation alerts for investigation
Thoughts: For any discrepancy detected, an alert should be created. This is testable.
Testable: yes - property

7.1 WHEN a merchant requests a refund for a captured transaction THEN the Payment Gateway SHALL validate that the original transaction exists and is refundable
Thoughts: For any refund request, we should validate the original transaction. This is testable across different transaction states.
Testable: yes - property

7.2 WHEN the Payment Gateway processes a refund THEN the Payment Gateway SHALL ensure the refund amount does not exceed the original transaction amount
Thoughts: For any refund, the amount constraint should be enforced. This is testable across different amounts.
Testable: yes - property

7.3 WHEN the Payment Gateway submits a refund to the PSP THEN the Payment Gateway SHALL receive confirmation of the refund status
Thoughts: For any refund submission, we should get confirmation. This is testable.
Testable: yes - property

7.4 WHEN a refund is completed THEN the Payment Gateway SHALL update the original transaction record with the refund details
Thoughts: For any completed refund, the original transaction should be updated. This is testable.
Testable: yes - property

7.5 WHEN a refund fails THEN the Payment Gateway SHALL log the failure reason and notify the merchant
Thoughts: For any refund failure, logging and notification should occur. This is testable.
Testable: yes - property

8.1 WHEN any service performs a payment operation THEN the Payment Gateway SHALL create an immutable audit log entry with timestamp and actor information
Thoughts: For any operation, an audit log should be created. This is testable across all operations.
Testable: yes - property

8.2 WHEN the Payment Gateway logs sensitive operations THEN the Payment Gateway SHALL include cryptographic integrity verification
Thoughts: For any sensitive operation log, we can verify cryptographic integrity. This is testable.
Testable: yes - property

8.3 WHEN the Payment Gateway creates audit logs THEN the Payment Gateway SHALL exclude raw PAN and CVV data from log entries
Thoughts: For any audit log, we should verify no raw PAN/CVV is present. This is testable across all logs.
Testable: yes - property

8.4 WHEN the Payment Gateway stores audit logs THEN the Payment Gateway SHALL retain logs for the minimum compliance period of seven years
Thoughts: This is about retention policy, not immediately testable in unit tests.
Testable: no

8.5 WHEN an administrator queries audit logs THEN the Payment Gateway SHALL enforce role-based access controls
Thoughts: For any audit log query, we should verify RBAC is enforced. This is testable across different roles.
Testable: yes - property


9.1 WHEN the Retry Engine encounters a transient PSP failure THEN the Retry Engine SHALL retry the request using exponential backoff
Thoughts: For any transient failure, we should see exponential backoff behavior. This is testable across failures.
Testable: yes - property

9.2 WHEN the Retry Engine detects repeated failures to a PSP THEN the Retry Engine SHALL open the circuit breaker for that PSP
Thoughts: For repeated failures above threshold, circuit should open. This is testable.
Testable: yes - property

9.3 WHILE a circuit breaker is open THEN the Retry Engine SHALL route transactions to alternative PSPs
Thoughts: While circuit is open, routing should go to alternatives. This is testable.
Testable: yes - property

9.4 WHEN the circuit breaker timeout expires THEN the Retry Engine SHALL attempt a test request to determine if the PSP has recovered
Thoughts: After timeout, a test request should be made. This is testable.
Testable: yes - property

9.5 WHEN maximum retry attempts are exhausted THEN the Retry Engine SHALL move the transaction to a dead letter queue for manual review
Thoughts: After max retries, transaction should go to DLQ. This is testable.
Testable: yes - property

10.1 WHEN a payment request enters the Payment Gateway THEN the Payment Gateway SHALL create a distributed trace with a unique trace identifier
Thoughts: For any payment request, a trace should be created. This is testable across all requests.
Testable: yes - property

10.2 WHEN a service processes part of a transaction THEN the Payment Gateway SHALL add span information to the distributed trace
Thoughts: For any service processing, span should be added. This is testable.
Testable: yes - property

10.3 WHEN the Payment Gateway collects metrics THEN the Payment Gateway SHALL expose Prometheus-compatible metrics endpoints
Thoughts: This is about metrics format, testable by verifying endpoint format.
Testable: yes - property

10.4 WHEN the Payment Gateway detects anomalies THEN the Payment Gateway SHALL generate real-time alerts
Thoughts: For any anomaly detection, alert should be generated. This is testable.
Testable: yes - property

10.5 WHEN an administrator views traces THEN the Payment Gateway SHALL provide end-to-end visibility of transaction flow across all services
Thoughts: For any trace query, complete flow should be visible. This is testable.
Testable: yes - property

11.1 WHEN the HSM generates a cryptographic key THEN the HSM SHALL use cryptographically secure random number generation
Thoughts: For any key generation, we can verify randomness properties. This is testable.
Testable: yes - property

11.2 WHEN the HSM stores a key THEN the HSM SHALL protect the key with hardware-level security controls
Thoughts: This is about hardware security, not directly testable in software tests.
Testable: no

11.3 WHEN a service requests a cryptographic operation THEN the HSM SHALL perform the operation without exposing the raw key
Thoughts: For any crypto operation, the key should never be exposed. This is testable.
Testable: yes - property

11.4 WHEN the HSM rotates keys THEN the HSM SHALL maintain access to previous key versions for decryption of existing data
Thoughts: After key rotation, old data should still be decryptable. This is a round-trip property.
Testable: yes - property

11.5 WHEN the HSM performs key operations THEN the HSM SHALL create audit log entries for all key access
Thoughts: For any key operation, audit log should be created. This is testable.
Testable: yes - property


12.1 WHEN the Payment Gateway receives a chargeback notification THEN the Payment Gateway SHALL create a dispute record linked to the original transaction
Thoughts: For any chargeback notification, a dispute record should be created. This is testable.
Testable: yes - property

12.2 WHEN a dispute is created THEN the Payment Gateway SHALL notify the merchant with dispute details and deadline information
Thoughts: For any dispute creation, merchant notification should occur. This is testable.
Testable: yes - property

12.3 WHEN a merchant submits dispute evidence THEN the Payment Gateway SHALL forward the evidence to the Acquirer
Thoughts: For any evidence submission, it should be forwarded. This is testable.
Testable: yes - property

12.4 WHEN the Acquirer resolves a dispute THEN the Payment Gateway SHALL update the dispute status and notify the merchant
Thoughts: For any dispute resolution, status update and notification should occur. This is testable.
Testable: yes - property

12.5 WHEN a chargeback is finalized THEN the Payment Gateway SHALL adjust the settlement records accordingly
Thoughts: For any finalized chargeback, settlement should be adjusted. This is testable.
Testable: yes - property

13.1 WHEN a merchant makes an API request THEN the Payment Gateway SHALL validate the API key or OAuth 2.0 token
Thoughts: For any API request, authentication should be validated. This is testable across all requests.
Testable: yes - property

13.2 WHEN the Payment Gateway validates credentials THEN the Payment Gateway SHALL verify that the merchant has permission for the requested operation
Thoughts: For any credential validation, authorization should be checked. This is testable.
Testable: yes - property

13.3 WHEN the Payment Gateway issues JWT tokens THEN the Payment Gateway SHALL include merchant identity and permissions in the token claims
Thoughts: For any JWT issuance, claims should include identity and permissions. This is testable.
Testable: yes - property

13.4 WHEN a JWT token expires THEN the Payment Gateway SHALL reject requests using the expired token
Thoughts: For any expired token, request should be rejected. This is testable.
Testable: yes - property

13.5 WHEN the Payment Gateway detects invalid authentication attempts THEN the Payment Gateway SHALL implement rate limiting and log the attempts
Thoughts: For any invalid auth attempt, rate limiting and logging should occur. This is testable.
Testable: yes - property

14.1 WHEN a payment state changes THEN the Payment Gateway SHALL publish an event to the appropriate Kafka topic
Thoughts: For any state change, an event should be published. This is testable across all state changes.
Testable: yes - property

14.2 WHEN a service publishes an event THEN the Payment Gateway SHALL validate the event against the schema registry
Thoughts: For any event publication, schema validation should occur. This is testable.
Testable: yes - property

14.3 WHEN a service consumes an event THEN the Payment Gateway SHALL process the event idempotently to handle duplicate deliveries
Thoughts: For any event, processing twice should have same effect as processing once. This is an idempotence property.
Testable: yes - property

14.4 WHEN event processing fails THEN the Payment Gateway SHALL retry with exponential backoff before moving to a dead letter topic
Thoughts: For any processing failure, retry with backoff should occur. This is testable.
Testable: yes - property

14.5 WHEN the Payment Gateway stores events THEN the Payment Gateway SHALL maintain event ordering within a partition key
Thoughts: For any events with same partition key, ordering should be maintained. This is testable.
Testable: yes - property

15.1 WHEN a merchant queries a transaction by identifier THEN the Payment Gateway SHALL return the current transaction status and history
Thoughts: For any transaction query, status and history should be returned. This is testable.
Testable: yes - property

15.2 WHEN the Payment Gateway returns transaction details THEN the Payment Gateway SHALL include masked card information but exclude raw PAN
Thoughts: For any transaction details response, PAN should be masked. This is testable across all responses.
Testable: yes - property

15.3 WHEN a merchant queries transactions by date range THEN the Payment Gateway SHALL return paginated results with appropriate filters
Thoughts: For any date range query, pagination should work correctly. This is testable.
Testable: yes - property

15.4 WHEN the Payment Gateway processes a status query THEN the Payment Gateway SHALL use cached data when available to minimize database load
Thoughts: For any status query, cache should be checked first. This is testable.
Testable: yes - property

15.5 WHEN a transaction is not found THEN the Payment Gateway SHALL return an appropriate error response
Thoughts: For any non-existent transaction query, error should be returned. This is testable.
Testable: yes - property


16.1 WHEN the Authorization Service receives a payment request THEN the Authorization Service SHALL coordinate with Tokenization Service, Fraud Detection Service, and 3D Secure Service in sequence
Thoughts: For any payment request, the orchestration sequence should be followed. This is testable.
Testable: yes - property

16.2 WHEN any service in the transaction flow fails THEN the Payment Gateway SHALL execute compensating transactions to maintain data consistency
Thoughts: For any service failure, compensating transactions should execute. This is testable across failure scenarios.
Testable: yes - property

16.3 WHEN the Authorization Service completes authorization THEN the Authorization Service SHALL publish payment events to Kafka for asynchronous processing by Settlement Service
Thoughts: For any completed authorization, event should be published. This is testable.
Testable: yes - property

16.4 WHEN services communicate internally THEN the Payment Gateway SHALL use gRPC for synchronous calls and Kafka events for asynchronous workflows
Thoughts: This is about communication protocol choice, verifiable by inspecting communication patterns.
Testable: yes - property

16.5 WHEN the Payment Gateway processes a transaction THEN the Payment Gateway SHALL maintain distributed transaction context across all service boundaries
Thoughts: For any transaction, context should be propagated. This is testable.
Testable: yes - property

17.1-17.5, 18.1-18.5, 19.1-19.5, 20.1-20.5: These are primarily operational and infrastructure requirements about availability, validation, rate limiting, and database optimization. Most are testable as properties verifying behavior across inputs.
Testable: yes - property (for most)

21.1 WHEN a merchant submits a payment request with an idempotency key THEN the Payment Gateway SHALL check for existing transactions with the same key
Thoughts: For any request with idempotency key, duplicate check should occur. This is testable.
Testable: yes - property

21.2 WHEN the Payment Gateway finds a matching idempotency key THEN the Payment Gateway SHALL return the original transaction result without reprocessing
Thoughts: For any duplicate idempotency key, same result should be returned. This is an idempotence property.
Testable: yes - property

21.3 WHEN the Payment Gateway stores idempotency keys THEN the Payment Gateway SHALL retain them for at least 24 hours
Thoughts: This is about retention duration, testable by verifying keys exist after storage.
Testable: yes - property

21.4 WHEN the Payment Gateway processes concurrent requests with the same idempotency key THEN the Payment Gateway SHALL use distributed locking to ensure only one processes
Thoughts: For concurrent requests with same key, only one should process. This is testable.
Testable: yes - property

21.5 WHEN the Payment Gateway completes a transaction THEN the Payment Gateway SHALL atomically store the result with the idempotency key
Thoughts: For any completed transaction, result and key should be stored atomically. This is testable.
Testable: yes - property

22.1-22.5, 23.1-23.5, 24.1-24.5, 25.1-25.5, 26.1-26.5, 27.1-27.5, 28.1-28.5, 29.1-29.5, 30.1-30.5: These cover webhooks, multi-currency, security, backups, compliance, performance, service mesh, schema versioning, and graceful degradation. Most are testable as properties.
Testable: yes - property (for most)

### Property Reflection

After reviewing all acceptance criteria, I identify the following consolidation opportunities:

1. **Encryption properties (1.2, 1.3, 2.3)** can be consolidated into a single comprehensive encryption property
2. **Audit logging properties (8.1, 8.3, 11.5)** share similar verification logic
3. **Event publishing properties (14.1, 16.3)** can be combined
4. **Idempotency properties (21.2, 14.3)** test the same core behavior
5. **Error handling properties (2.5, 7.5, 15.5)** follow similar patterns

After consolidation, we focus on unique, high-value properties that provide comprehensive coverage.



### Core Correctness Properties

**Property 1: Tokenization Round Trip**
*For any* valid PAN and expiry date, tokenizing then detokenizing should return the original PAN and expiry values unchanged.
**Validates: Requirements 2.4**

**Property 2: PAN Never Stored Raw**
*For any* payment transaction processed, querying the database should never return raw PAN data - all PAN fields must be either tokenized or encrypted.
**Validates: Requirements 1.1, 1.3**

**Property 3: Encryption Uses AES-256-GCM**
*For any* sensitive data encrypted by the system, the encryption algorithm identifier should be AES-256-GCM and decryption should successfully recover the original data.
**Validates: Requirements 1.2, 2.3**

**Property 4: Token Uniqueness**
*For any* set of distinct PANs, the generated tokens must all be unique - no two different PANs should produce the same token.
**Validates: Requirements 2.1**

**Property 5: Invalid Token Rejection**
*For any* malformed or non-existent token, detokenization requests should be rejected with an error and an audit log entry should be created.
**Validates: Requirements 2.5**

**Property 6: PSP Routing Consistency**
*For any* payment request with specific merchant configuration and routing rules, the same PSP should be selected consistently given the same inputs.
**Validates: Requirements 3.1**

**Property 7: Failover on PSP Error**
*For any* PSP error response, if alternative PSPs are configured, the system should attempt failover to at least one alternative PSP before failing the transaction.
**Validates: Requirements 3.2**

**Property 8: Required PSP Fields Present**
*For any* PSP authorization request, all required fields (amount, currency, card token, merchant ID) must be present in the request payload.
**Validates: Requirements 3.3**

**Property 9: Fraud Score Range**
*For any* transaction evaluated by the fraud detection service, the fraud score must be a decimal value between 0.00 and 1.00 inclusive.
**Validates: Requirements 4.1**

**Property 10: High Risk Triggers 3DS**
*For any* transaction with fraud score above the configured threshold (e.g., 0.75), the system should require 3D Secure authentication.
**Validates: Requirements 4.3**

**Property 11: Blacklist Immediate Rejection**
*For any* transaction from a source on the blacklist (IP, card hash, device fingerprint), the transaction should be rejected before authorization is attempted.
**Validates: Requirements 4.5**

**Property 12: 3DS Authentication Data Included**
*For any* successfully authenticated 3D Secure transaction, the authorization request to the PSP must include CAVV, ECI, and XID values.
**Validates: Requirements 5.4**

**Property 13: Settlement Aggregation Completeness**
*For any* settlement batch created, the sum of all transaction amounts in the batch should equal the batch total_amount field.
**Validates: Requirements 6.1**

**Property 14: Refund Amount Constraint**
*For any* refund request, the sum of all refunds for a transaction plus the new refund amount must not exceed the original transaction amount.
**Validates: Requirements 7.2**

**Property 15: Audit Log Immutability**
*For any* audit log entry created, subsequent queries for that entry should return identical data - audit logs cannot be modified.
**Validates: Requirements 8.1**

**Property 16: PAN Redaction in Logs**
*For any* audit log entry, the log content should not contain raw PAN or CVV data - only masked or tokenized values should appear.
**Validates: Requirements 8.3**

**Property 17: Exponential Backoff Timing**
*For any* retry sequence, the delay between retry attempts should increase exponentially (e.g., 1s, 2s, 4s, 8s) up to a maximum delay.
**Validates: Requirements 9.1**

**Property 18: Circuit Breaker Opens on Threshold**
*For any* PSP that fails more than the configured threshold (e.g., 5 consecutive failures), the circuit breaker should transition to OPEN state.
**Validates: Requirements 9.2**

**Property 19: DLQ After Max Retries**
*For any* transaction that fails after maximum retry attempts, the transaction should be moved to the dead letter queue with failure details.
**Validates: Requirements 9.5**

**Property 20: Distributed Trace Propagation**
*For any* payment request, all service calls in the transaction flow should share the same trace ID in their span context.
**Validates: Requirements 10.1, 10.2**


**Property 21: HSM Key Never Exposed**
*For any* cryptographic operation performed by the HSM, the raw key material should never be returned in the response - only encrypted/decrypted data or signatures.
**Validates: Requirements 11.3**

**Property 22: Key Rotation Backward Compatibility**
*For any* data encrypted with an old key version, after key rotation, the data should still be decryptable using the old key version identifier.
**Validates: Requirements 11.4**

**Property 23: Chargeback Creates Dispute**
*For any* chargeback notification received, a dispute record should be created and linked to the original payment transaction.
**Validates: Requirements 12.1**

**Property 24: Authentication Required**
*For any* API request without valid authentication (API key or JWT token), the request should be rejected with HTTP 401 status.
**Validates: Requirements 13.1**

**Property 25: Authorization Enforced**
*For any* authenticated request, if the merchant lacks permission for the requested operation, the request should be rejected with HTTP 403 status.
**Validates: Requirements 13.2**

**Property 26: Event Schema Validation**
*For any* event published to Kafka, the event payload must validate successfully against the registered schema in the Schema Registry.
**Validates: Requirements 14.2**

**Property 27: Idempotent Event Processing**
*For any* event processed multiple times with the same event ID, the system state should be identical to processing it once.
**Validates: Requirements 14.3**

**Property 28: Event Ordering Preserved**
*For any* sequence of events with the same partition key, consuming the events should maintain the original publication order.
**Validates: Requirements 14.5**

**Property 29: PAN Masking in Responses**
*For any* transaction query response, card numbers should be masked showing only the last 4 digits (e.g., ****1234).
**Validates: Requirements 15.2**

**Property 30: Idempotency Key Deduplication**
*For any* payment request submitted multiple times with the same idempotency key, only one payment transaction should be created and all requests should return the same result.
**Validates: Requirements 21.2**

**Property 31: Concurrent Idempotency Protection**
*For any* concurrent payment requests with the same idempotency key, distributed locking should ensure only one request processes while others wait.
**Validates: Requirements 21.4**

**Property 32: Webhook HMAC Signature**
*For any* webhook sent to a merchant, the payload should include a valid HMAC signature that can be verified using the merchant's webhook secret.
**Validates: Requirements 22.2**

**Property 33: Currency Conversion Consistency**
*For any* multi-currency transaction, converting from currency A to B and back to A should result in an amount within acceptable rounding tolerance.
**Validates: Requirements 23.2**

**Property 34: TLS 1.3 Enforcement**
*For any* external API connection, the TLS version negotiated should be 1.3 or higher - older versions should be rejected.
**Validates: Requirements 1.5**

**Property 35: Rate Limit Enforcement**
*For any* merchant exceeding their configured rate limit, additional requests should be rejected with HTTP 429 and include a Retry-After header.
**Validates: Requirements 19.1**

**Property 36: Input Validation**
*For any* payment request, all required fields must be present and valid according to defined schemas before processing begins.
**Validates: Requirements 18.1**

**Property 37: Luhn Checksum Validation**
*For any* card number provided, the Luhn checksum algorithm should validate correctly before tokenization proceeds.
**Validates: Requirements 18.2**

**Property 38: Double-Entry Accounting**
*For any* financial transaction recorded, the sum of debits must equal the sum of credits in the accounting entries.
**Validates: Requirements 18.3**

**Property 39: Service Orchestration Sequence**
*For any* payment authorization, services should be called in the correct sequence: Tokenization → Fraud Detection → 3D Secure (if required) → PSP Authorization.
**Validates: Requirements 16.1**

**Property 40: Compensating Transaction on Failure**
*For any* transaction that fails after partial completion, compensating actions should be executed to maintain consistency (e.g., releasing reserved funds).
**Validates: Requirements 16.2**



## Error Handling

### Error Classification

**1. Validation Errors (4xx)**
- Invalid card number format
- Missing required fields
- Invalid currency code
- Amount out of acceptable range
- Expired card

**Response:**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid card number format",
    "field": "card.number",
    "details": "Card number must be 13-19 digits"
  }
}
```

**2. Authentication/Authorization Errors (401/403)**
- Invalid API key
- Expired JWT token
- Insufficient permissions
- Merchant account suspended

**Response:**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid API key",
    "details": "The provided API key is invalid or has been revoked"
  }
}
```

**3. Business Logic Errors (422)**
- Insufficient funds
- Card declined by issuer
- Fraud check failed
- Refund exceeds original amount
- Transaction already captured

**Response:**
```json
{
  "error": {
    "code": "TRANSACTION_DECLINED",
    "message": "Card declined by issuer",
    "decline_code": "insufficient_funds",
    "details": "The card has insufficient funds for this transaction"
  }
}
```

**4. Rate Limiting Errors (429)**
- Too many requests
- Velocity limit exceeded

**Response:**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests",
    "retry_after": 60
  }
}
```

**5. System Errors (5xx)**
- Database connection failure
- PSP timeout
- Internal service error
- Circuit breaker open

**Response:**
```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "An internal error occurred",
    "request_id": "req_abc123",
    "details": "Please contact support with this request ID"
  }
}
```

### Error Handling Strategies

**Retry Strategy:**
- Transient errors (network timeouts, 503 responses): Exponential backoff with jitter
- Idempotent operations: Safe to retry automatically
- Non-idempotent operations: Require explicit idempotency key

**Circuit Breaker:**
- Failure threshold: 5 consecutive failures
- Timeout: 30 seconds
- Half-open test: Single request after timeout
- Success threshold to close: 3 consecutive successes

**Fallback Mechanisms:**
- PSP failover: Route to alternative PSP
- Fraud service down: Use rule-based fraud checks
- 3DS service down: Process without 3DS (with logging)
- Cache unavailable: Direct database queries

**Compensating Transactions:**
- Authorization fails after tokenization: Token remains valid for future use
- Capture fails after authorization: Authorization can be voided
- Settlement fails: Retry in next batch
- Webhook delivery fails: Retry with exponential backoff



## Testing Strategy

### Overview

The testing strategy employs a dual approach combining unit tests for specific scenarios and property-based tests for universal correctness guarantees. This comprehensive approach ensures both concrete bug detection and general correctness verification.

### Property-Based Testing

**Framework Selection:**
- **Java services**: JUnit 5 + jqwik (property-based testing library)
- **Go services**: testing package + gopter (property-based testing)
- **Rust services**: proptest crate

**Configuration:**
- Minimum iterations per property: 100 runs
- Seed: Randomized (logged for reproducibility)
- Shrinking: Enabled to find minimal failing cases

**Property Test Structure:**

Each property-based test must:
1. Be tagged with a comment referencing the design document property
2. Use format: `**Feature: payment-acquiring-gateway, Property {number}: {property_text}**`
3. Generate random valid inputs using smart generators
4. Execute the operation under test
5. Assert the property holds for all generated inputs

**Example Property Test (Java/jqwik):**

```java
/**
 * Feature: payment-acquiring-gateway, Property 1: Tokenization Round Trip
 * For any valid PAN and expiry date, tokenizing then detokenizing 
 * should return the original PAN and expiry values unchanged.
 */
@Property
void tokenizationRoundTrip(@ForAll @CardNumber String pan,
                          @ForAll @IntRange(min = 1, max = 12) int month,
                          @ForAll @IntRange(min = 2025, max = 2035) int year) {
    // Tokenize
    TokenizeResponse tokenResponse = tokenizationService.tokenize(
        new TokenizeRequest(pan, month, year)
    );
    
    // Detokenize
    DetokenizeResponse detokenResponse = tokenizationService.detokenize(
        tokenResponse.getToken()
    );
    
    // Assert round trip
    assertThat(detokenResponse.getPan()).isEqualTo(pan);
    assertThat(detokenResponse.getExpiryMonth()).isEqualTo(month);
    assertThat(detokenResponse.getExpiryYear()).isEqualTo(year);
}
```

**Smart Generators:**

Generators should constrain inputs to valid domain values:

```java
@Provide
Arbitrary<String> validCardNumbers() {
    return Arbitraries.strings()
        .numeric()
        .ofLength(16)
        .filter(this::passesLuhnCheck);
}

@Provide
Arbitrary<PaymentRequest> validPaymentRequests() {
    return Combinators.combine(
        validCardNumbers(),
        amounts(),
        currencies(),
        merchantIds()
    ).as(PaymentRequest::new);
}
```

### Unit Testing

**Scope:**
- Specific edge cases (empty inputs, boundary values)
- Error conditions and exception handling
- Integration points between components
- Mock external dependencies (PSPs, banks)

**Example Unit Tests:**

```java
@Test
void shouldRejectEmptyCardNumber() {
    PaymentRequest request = new PaymentRequest("", 100.00, "USD");
    
    assertThatThrownBy(() -> authService.processPayment(request))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Card number is required");
}

@Test
void shouldHandlePSPTimeout() {
    when(pspClient.authorize(any())).thenThrow(new TimeoutException());
    
    PaymentResponse response = authService.processPayment(validRequest);
    
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(response.getErrorCode()).isEqualTo("PSP_TIMEOUT");
}
```

### Integration Testing

**Scope:**
- End-to-end payment flows
- Service-to-service communication
- Database transactions
- Kafka event publishing/consuming
- External API integrations (with test environments)

**Test Containers:**
- PostgreSQL test database
- Redis test instance
- Kafka test cluster
- Mock PSP servers

**Example Integration Test:**

```java
@SpringBootTest
@Testcontainers
class PaymentFlowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
    
    @Test
    void shouldProcessPaymentEndToEnd() {
        // Given
        PaymentRequest request = createValidPaymentRequest();
        
        // When
        PaymentResponse response = restTemplate.postForObject(
            "/api/v1/payments",
            request,
            PaymentResponse.class
        );
        
        // Then
        assertThat(response.getStatus()).isEqualTo("AUTHORIZED");
        assertThat(response.getPaymentId()).isNotNull();
        
        // Verify database
        Payment payment = paymentRepository.findById(response.getPaymentId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        
        // Verify event published
        ConsumerRecord<String, PaymentEvent> event = kafkaConsumer.poll();
        assertThat(event.value().getEventType()).isEqualTo("PAYMENT_AUTHORIZED");
    }
}
```

### Performance Testing

**Load Testing:**
- Tool: JMeter or Gatling
- Target: 10,000 TPS sustained
- Duration: 1 hour
- Metrics: Latency (p50, p95, p99), throughput, error rate

**Stress Testing:**
- Gradually increase load until system degrades
- Identify breaking points
- Verify graceful degradation

**Soak Testing:**
- Run at 70% capacity for 24 hours
- Monitor for memory leaks
- Check resource exhaustion

### Security Testing

**Penetration Testing:**
- SQL injection attempts
- XSS attacks
- CSRF protection
- Authentication bypass attempts
- Authorization escalation

**PCI DSS Compliance Testing:**
- Verify no raw PAN in logs
- Validate encryption at rest
- Check TLS configuration
- Audit access controls
- Test key rotation procedures

### Test Coverage Goals

- **Unit test coverage**: >80% line coverage
- **Property test coverage**: All 40 correctness properties implemented
- **Integration test coverage**: All critical user journeys
- **API test coverage**: All REST endpoints
- **Error scenario coverage**: All error codes tested

### Continuous Testing

**CI/CD Pipeline:**
1. Unit tests: Run on every commit
2. Property tests: Run on every commit (100 iterations)
3. Integration tests: Run on PR merge
4. Performance tests: Run nightly
5. Security scans: Run weekly

**Test Environments:**
- Development: Local with test containers
- Staging: Full environment with mock PSPs
- Production: Synthetic monitoring only

