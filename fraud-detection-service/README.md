# Fraud Detection Service

Real-time fraud detection and risk assessment service for the Payment Acquiring Gateway.

## Overview

The Fraud Detection Service evaluates payment transactions for fraud risk using multiple detection mechanisms:

- **ML-based fraud scoring**: Machine learning models to calculate fraud probability
- **Velocity checks**: Monitor transaction frequency to detect suspicious patterns
- **Geolocation risk assessment**: Evaluate risk based on IP and billing address locations
- **Configurable rules engine**: Custom fraud rules with priority-based evaluation
- **Blacklist/whitelist management**: Immediate rejection of known fraudulent sources
- **Fraud alert creation**: Automatic alerts for high-risk transactions requiring manual review

## Architecture

### Components

1. **FraudDetectionService**: Core service orchestrating fraud evaluation
2. **VelocityCheckService**: Redis-based velocity limit enforcement
3. **GeolocationService**: IP and address-based risk assessment
4. **MLFraudScoringService**: Machine learning fraud scoring (placeholder for production ML model)
5. **gRPC Server**: External API for fraud evaluation requests

### Risk Thresholds

- **Low Risk** (score < 0.50): CLEAN status, no 3DS required
- **Medium Risk** (0.50 ≤ score < 0.75): REVIEW status, 3DS required
- **High Risk** (score ≥ 0.75): BLOCK status, 3DS required, fraud alert created

## API

### gRPC Service

```protobuf
service FraudDetectionService {
  rpc EvaluateTransaction(FraudRequest) returns (FraudResponse);
  rpc UpdateRules(RuleUpdateRequest) returns (RuleUpdateResponse);
  rpc AddToBlacklist(BlacklistRequest) returns (BlacklistResponse);
  rpc RemoveFromBlacklist(BlacklistRequest) returns (BlacklistResponse);
}
```

### Fraud Evaluation Request

```json
{
  "transaction_id": "txn_123",
  "amount": "1000.00",
  "currency": "USD",
  "card_token": "tok_abc123",
  "ip_address": "192.168.1.1",
  "device_fingerprint": "device_xyz",
  "billing_address": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "postal_code": "10001",
    "country": "US"
  },
  "merchant_id": "merchant_123"
}
```

### Fraud Evaluation Response

```json
{
  "fraud_score": 0.35,
  "status": "CLEAN",
  "triggered_rules": [],
  "require_3ds": false,
  "risk_level": "LOW"
}
```

## Fraud Detection Logic

### 1. Blacklist Check (Immediate Rejection)

Checks if the transaction source is blacklisted:
- IP address
- Device fingerprint
- Card hash

If blacklisted → **BLOCK** with score 1.0

### 2. Velocity Checks

Monitors transaction frequency:
- Max 10 transactions per card per hour
- Max 20 transactions per IP per hour
- Max 100 transactions per merchant per minute

Exceeding limits triggers `VELOCITY_LIMIT_EXCEEDED` rule.

### 3. Geolocation Risk Assessment

Evaluates risk based on location:
- High-risk countries (configurable list)
- IP country vs billing country mismatch
- Missing location information

### 4. Custom Rules Evaluation

Evaluates enabled fraud rules in priority order:
- Amount-based rules (e.g., "amount > 10000")
- Country-based rules (e.g., "country = 'NG'")
- Time-based rules (e.g., "hour >= 22 OR hour <= 6")

### 5. ML Fraud Scoring

Calculates fraud probability using:
- Transaction amount
- Missing information penalties
- Time of day risk
- Historical patterns (in production)

### 6. Final Score Calculation

Weighted combination:
- ML score: 60%
- Geolocation score: 30%
- Rule penalties: up to 30%

Final score is clamped to [0.0, 1.0] range.

## Database Schema

### fraud_rules

```sql
CREATE TABLE fraud_rules (
  id UUID PRIMARY KEY,
  rule_name VARCHAR(255) NOT NULL,
  rule_condition TEXT NOT NULL,
  priority INTEGER NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### fraud_alerts

```sql
CREATE TABLE fraud_alerts (
  id UUID PRIMARY KEY,
  transaction_id VARCHAR(100) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  fraud_score DOUBLE PRECISION NOT NULL,
  status VARCHAR(20) NOT NULL,
  triggered_rules TEXT,
  merchant_id VARCHAR(50) NOT NULL,
  ip_address VARCHAR(45),
  device_fingerprint VARCHAR(255),
  created_at TIMESTAMP NOT NULL,
  reviewed_at TIMESTAMP,
  reviewed_by VARCHAR(100),
  review_notes TEXT
);
```

### blacklist

```sql
CREATE TABLE blacklist (
  id UUID PRIMARY KEY,
  entry_type VARCHAR(50) NOT NULL,
  value VARCHAR(255) NOT NULL,
  reason TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  created_by VARCHAR(100),
  INDEX idx_blacklist_type_value (entry_type, value)
);
```

## Configuration

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_gateway
    username: postgres
    password: postgres
  
  data:
    redis:
      host: localhost
      port: 6379

grpc:
  server:
    port: 8447

server:
  port: 8547
```

## Testing

### Property-Based Tests

Three correctness properties are tested with 100 iterations each:

1. **Property 9: Fraud Score Range** - All fraud scores must be between 0.0 and 1.0
2. **Property 10: High Risk Triggers 3DS** - Transactions with score ≥ 0.75 must require 3DS
3. **Property 11: Blacklist Immediate Rejection** - Blacklisted sources must be rejected immediately

### Unit Tests

Comprehensive unit tests covering:
- Velocity limit enforcement
- Geolocation risk scoring
- Rule evaluation
- Blacklist checking
- Fraud alert creation
- Edge cases and error handling

### Running Tests

```bash
mvn test -pl fraud-detection-service
```

## Building and Running

### Build

```bash
mvn clean package -pl fraud-detection-service
```

### Run

```bash
java -jar fraud-detection-service/target/fraud-detection-service-1.0.0-SNAPSHOT.jar
```

### Docker

```bash
docker build -t fraud-detection-service fraud-detection-service/
docker run -p 8447:8447 -p 8547:8547 fraud-detection-service
```

## Integration

### From Authorization Service

```java
FraudRequest request = FraudRequest.newBuilder()
    .setTransactionId(transactionId)
    .setAmount(amount.toString())
    .setCurrency(currency)
    .setCardToken(cardToken)
    .setIpAddress(ipAddress)
    .setDeviceFingerprint(deviceFingerprint)
    .setBillingAddress(address)
    .setMerchantId(merchantId)
    .build();

FraudResponse response = fraudDetectionStub.evaluateTransaction(request);

if (response.getStatus() == FraudStatus.BLOCK) {
    // Reject transaction
} else if (response.getRequire3Ds()) {
    // Initiate 3D Secure authentication
}
```

## Monitoring

### Metrics

- `fraud.evaluations.total` - Total fraud evaluations
- `fraud.evaluations.blocked` - Transactions blocked
- `fraud.evaluations.review` - Transactions flagged for review
- `fraud.score.histogram` - Distribution of fraud scores
- `fraud.velocity.exceeded` - Velocity limit violations
- `fraud.blacklist.hits` - Blacklist matches

### Health Check

```bash
curl http://localhost:8547/actuator/health
```

### Prometheus Metrics

```bash
curl http://localhost:8547/actuator/prometheus
```

## Production Considerations

### ML Model Integration

Replace `MLFraudScoringService` with actual ML model:
- TensorFlow Serving
- PyTorch model server
- AWS SageMaker endpoint
- Custom model API

### Geolocation Service

Integrate real IP geolocation service:
- MaxMind GeoIP2
- IP2Location
- ipstack API

### Rule Engine

For production, consider using:
- Drools rule engine
- Custom DSL parser
- Decision table approach

### Performance

- Cache fraud rules in Redis
- Batch fraud alert creation
- Async processing for non-critical checks
- Connection pooling for database and Redis

## Requirements Validation

This implementation validates:

- **Requirement 4.1**: ML-based fraud scoring ✓
- **Requirement 4.2**: Velocity checks against transaction history ✓
- **Requirement 4.3**: High-risk transactions trigger 3DS ✓
- **Requirement 4.4**: Fraud alerts for suspicious patterns ✓
- **Requirement 4.5**: Blacklist immediate rejection ✓
