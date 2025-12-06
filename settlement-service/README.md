# Settlement Service

The Settlement Service handles batch processing of captured payments, reconciliation with acquirers, and dispute/chargeback management.

## Features

### Settlement Batch Processing
- Automated daily settlement batch creation (scheduled at 2 AM)
- Groups payments by merchant and currency
- Calculates fees and net amounts
- Generates settlement files in acquirer format
- Submits batches to acquirers via SFTP

### Reconciliation
- Compares submitted transactions with acquirer reports
- Validates totals and transaction counts
- Flags discrepancies for investigation

### Dispute/Chargeback Handling
- Creates dispute records from chargeback notifications
- Links disputes to original payment transactions
- Manages evidence submission workflow
- Tracks dispute resolution
- Adjusts settlement records for finalized chargebacks

## Domain Models

### SettlementBatch
- Aggregates captured payments for settlement
- Tracks total amount and transaction count
- Maintains status (PENDING, PROCESSING, SETTLED, FAILED)
- Links to acquirer batch ID

### SettlementTransaction
- Individual transaction within a batch
- Stores gross amount, fees, and net amount
- Links to original payment

### Dispute
- Represents a chargeback or dispute
- Tracks status (OPEN, PENDING_EVIDENCE, WON, LOST)
- Stores deadline and resolution information
- Links to original payment and merchant

## API Endpoints

The service runs on port 8449 and exposes:
- Health check: `/actuator/health`
- Metrics: `/actuator/metrics`
- Prometheus metrics: `/actuator/prometheus`

## Configuration

See `application.yml` for configuration options:
- Database connection
- Kafka settings
- Scheduled job timing

## Testing

Property-based tests validate:
- **Property 13**: Settlement batch total equals sum of transaction amounts
- **Property 23**: Chargeback notifications create dispute records

Unit tests cover:
- Batch creation logic
- Reconciliation scenarios
- Dispute handling workflows

## Running the Service

```bash
mvn spring-boot:run
```

## Dependencies

- Spring Boot 3.2.0
- PostgreSQL (for data persistence)
- Kafka (for event publishing)
- Micrometer (for metrics)
