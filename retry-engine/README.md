# Retry Engine Service

The Retry Engine is a Rust-based microservice that provides intelligent retry logic with exponential backoff, circuit breaker pattern implementation, and dead letter queue (DLQ) processing for the Payment Acquiring Gateway.

## Features

- **Exponential Backoff**: Configurable retry delays that increase exponentially with optional jitter
- **Circuit Breaker**: Prevents cascading failures by opening circuits after threshold failures
- **Dead Letter Queue**: Captures transactions that exceed maximum retry attempts
- **Multi-PSP Support**: Manages circuit breakers per PSP for intelligent failover
- **gRPC API**: High-performance service interface

## Architecture

The Retry Engine consists of several key components:

- **RetryPolicy**: Calculates exponential backoff delays with jitter
- **CircuitBreaker**: Implements the circuit breaker pattern with CLOSED, OPEN, and HALF_OPEN states
- **DeadLetterQueue**: Stores failed transactions for manual review
- **RetryEngineService**: gRPC service that orchestrates retry logic

## Configuration

### Retry Configuration

```rust
RetryConfig {
    max_attempts: 5,              // Maximum retry attempts
    initial_delay_ms: 1000,       // Initial delay (1 second)
    max_delay_ms: 60000,          // Maximum delay (60 seconds)
    backoff_multiplier: 2.0,      // Exponential multiplier
    jitter: true,                 // Add random jitter (±20%)
}
```

### Circuit Breaker Configuration

```rust
CircuitBreakerConfig {
    failure_threshold: 5,         // Failures before opening
    success_threshold: 3,         // Successes to close from half-open
    timeout_duration_ms: 30000,   // Timeout before half-open (30 seconds)
}
```

## gRPC API

### ScheduleRetry

Schedule a retry for a failed transaction.

```protobuf
rpc ScheduleRetry(RetryRequest) returns (RetryResponse);
```

### GetCircuitStatus

Get the current status of a circuit breaker for a PSP.

```protobuf
rpc GetCircuitStatus(CircuitRequest) returns (CircuitResponse);
```

### GetRetryStatus

Get the retry status of a transaction.

```protobuf
rpc GetRetryStatus(RetryStatusRequest) returns (RetryStatusResponse);
```

## Building

```bash
cargo build --release
```

## Running

```bash
cargo run
```

The service will start on `[::1]:8450`.

## Testing

### Run all tests

```bash
cargo test
```

### Run property-based tests

```bash
cargo test --test exponential_backoff_property_test
cargo test --test circuit_breaker_property_test
cargo test --test dlq_property_test
```

### Run unit tests

```bash
cargo test --test retry_scenarios_test
```

## Correctness Properties

The Retry Engine implements three key correctness properties:

### Property 17: Exponential Backoff Timing

*For any retry sequence, the delay between retry attempts should increase exponentially (e.g., 1s, 2s, 4s, 8s) up to a maximum delay.*

**Validates: Requirements 9.1**

### Property 18: Circuit Breaker Opens on Threshold

*For any PSP that fails more than the configured threshold (e.g., 5 consecutive failures), the circuit breaker should transition to OPEN state.*

**Validates: Requirements 9.2**

### Property 19: DLQ After Max Retries

*For any transaction that fails after maximum retry attempts, the transaction should be moved to the dead letter queue with failure details.*

**Validates: Requirements 9.5**

## Circuit Breaker States

### CLOSED
- Normal operation
- Requests proceed normally
- Failures are counted
- Opens after reaching failure threshold

### OPEN
- Circuit is broken
- Requests are blocked
- After timeout, transitions to HALF_OPEN

### HALF_OPEN
- Testing if service recovered
- Limited requests allowed
- Success closes circuit
- Failure reopens circuit

## Exponential Backoff Example

With `initial_delay=1000ms`, `multiplier=2.0`, `max_delay=60000ms`:

```
Attempt 0: 0ms (immediate)
Attempt 1: 1000ms (1s)
Attempt 2: 2000ms (2s)
Attempt 3: 4000ms (4s)
Attempt 4: 8000ms (8s)
Attempt 5: 16000ms (16s)
Attempt 6: 32000ms (32s)
Attempt 7: 60000ms (60s, capped)
```

With jitter enabled, each delay varies by ±20%.

## Dead Letter Queue

Transactions are moved to the DLQ when:
- Maximum retry attempts are exhausted
- Circuit breaker is open and retries are not possible

DLQ entries contain:
- Transaction ID
- PSP name
- Original payload
- Attempt count
- Last error message
- Timestamp

## Integration

The Retry Engine integrates with:
- **Authorization Service**: Receives retry requests for failed PSP calls
- **Redis**: Stores circuit breaker state (future enhancement)
- **Monitoring**: Exposes metrics for retry attempts, circuit breaker states, and DLQ size

## Performance

- Handles thousands of concurrent retry requests
- Low-latency circuit breaker checks (<1ms)
- Efficient in-memory state management
- Async/await for non-blocking operations

## Requirements

- Rust 1.70+
- Protocol Buffers compiler (protoc)
- Redis (for persistent state storage)

## License

Part of the Payment Acquiring Gateway system.
