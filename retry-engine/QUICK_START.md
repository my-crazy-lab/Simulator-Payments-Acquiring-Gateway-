# Retry Engine - Quick Start Guide

## Prerequisites

### Install Rust

If Rust is not installed, install it using rustup:

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```

Verify installation:

```bash
rustc --version
cargo --version
```

### Install Protocol Buffers Compiler

**Ubuntu/Debian:**
```bash
sudo apt-get install protobuf-compiler
```

**macOS:**
```bash
brew install protobuf
```

## Building the Service

1. Navigate to the retry-engine directory:
```bash
cd retry-engine
```

2. Build the project:
```bash
cargo build
```

For optimized release build:
```bash
cargo build --release
```

## Running Tests

### Run All Tests
```bash
cargo test
```

### Run Property-Based Tests Only
```bash
make test-property
```

Or individually:
```bash
cargo test --test exponential_backoff_property_test
cargo test --test circuit_breaker_property_test
cargo test --test dlq_property_test
```

### Run Unit Tests Only
```bash
make test-unit
```

Or:
```bash
cargo test --test retry_scenarios_test
```

### Run Tests with Output
```bash
cargo test -- --nocapture
```

### Run Specific Test
```bash
cargo test exponential_backoff_increases_exponentially
```

## Running the Service

### Development Mode
```bash
cargo run
```

### Production Mode
```bash
cargo run --release
```

The service will start on `[::1]:8450` (IPv6 localhost).

## Testing the gRPC Service

### Using grpcurl

Install grpcurl:
```bash
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

List services:
```bash
grpcurl -plaintext [::1]:8450 list
```

Schedule a retry:
```bash
grpcurl -plaintext -d '{
  "transaction_id": "txn_123",
  "psp_name": "stripe",
  "payload": "dGVzdA==",
  "attempt_number": 1,
  "operation_type": "authorization"
}' [::1]:8450 retry.RetryEngine/ScheduleRetry
```

Get circuit status:
```bash
grpcurl -plaintext -d '{
  "psp_name": "stripe"
}' [::1]:8450 retry.RetryEngine/GetCircuitStatus
```

Get retry status:
```bash
grpcurl -plaintext -d '{
  "transaction_id": "txn_123"
}' [::1]:8450 retry.RetryEngine/GetRetryStatus
```

## Development Workflow

### Format Code
```bash
cargo fmt
```

### Run Linter
```bash
cargo clippy
```

### Check Without Building
```bash
cargo check
```

### Watch Mode (requires cargo-watch)
```bash
cargo install cargo-watch
cargo watch -x run
```

## Configuration

Edit `src/main.rs` to change default configurations:

```rust
let retry_config = RetryConfig {
    max_attempts: 5,
    initial_delay_ms: 1000,
    max_delay_ms: 60000,
    backoff_multiplier: 2.0,
    jitter: true,
};

let circuit_config = CircuitBreakerConfig {
    failure_threshold: 5,
    success_threshold: 3,
    timeout_duration_ms: 30000,
};
```

## Troubleshooting

### Proto Compilation Errors

If you see protobuf compilation errors:
```bash
# Install protoc
sudo apt-get install protobuf-compiler

# Clean and rebuild
cargo clean
cargo build
```

### Port Already in Use

If port 8450 is in use, edit `src/main.rs`:
```rust
let addr = "[::1]:8451".parse()?;  // Change port
```

### Test Failures

Run tests with verbose output:
```bash
cargo test -- --nocapture --test-threads=1
```

## Project Structure

```
retry-engine/
├── proto/              # Protocol Buffer definitions
├── src/                # Source code
│   ├── lib.rs         # Library exports
│   ├── main.rs        # Service entry point
│   ├── retry_policy.rs
│   ├── circuit_breaker.rs
│   ├── dlq.rs
│   └── server.rs
├── tests/             # Test files
├── Cargo.toml         # Dependencies
├── build.rs           # Build script
└── Makefile           # Build commands
```

## Next Steps

1. ✅ Build the project: `cargo build`
2. ✅ Run tests: `cargo test`
3. ✅ Start the service: `cargo run`
4. 🔄 Integrate with Authorization Service
5. 🔄 Add Redis persistence
6. 🔄 Add monitoring and metrics

## Useful Commands

```bash
# Clean build artifacts
cargo clean

# Update dependencies
cargo update

# Generate documentation
cargo doc --open

# Run benchmarks (if added)
cargo bench

# Check for outdated dependencies
cargo outdated
```

## Performance Testing

### Load Testing with ghz

Install ghz:
```bash
go install github.com/bojand/ghz/cmd/ghz@latest
```

Run load test:
```bash
ghz --insecure \
  --proto proto/retry.proto \
  --call retry.RetryEngine/ScheduleRetry \
  -d '{"transaction_id":"txn_{{.RequestNumber}}","psp_name":"stripe","attempt_number":1}' \
  -n 10000 \
  -c 100 \
  [::1]:8450
```

## Monitoring

### View Logs

The service uses `tracing` for structured logging. Logs are output to stdout.

Set log level:
```bash
RUST_LOG=debug cargo run
```

Log levels: `error`, `warn`, `info`, `debug`, `trace`

## Integration with Payment Gateway

The Retry Engine integrates with:

1. **Authorization Service**: Receives retry requests for failed PSP calls
2. **Redis**: Stores circuit breaker state (future)
3. **Kafka**: Publishes DLQ events (future)
4. **Prometheus**: Exposes metrics (future)

## Support

For issues or questions:
- Check `IMPLEMENTATION_STATUS.md` for current status
- Review `README.md` for detailed documentation
- Check test files for usage examples

## Quick Reference

| Command | Description |
|---------|-------------|
| `cargo build` | Build project |
| `cargo test` | Run all tests |
| `cargo run` | Start service |
| `cargo fmt` | Format code |
| `cargo clippy` | Run linter |
| `make help` | Show all make commands |
