.PHONY: build test run clean proto help

help:
	@echo "Retry Engine - Makefile commands:"
	@echo "  make build    - Build the project"
	@echo "  make test     - Run all tests"
	@echo "  make run      - Run the service"
	@echo "  make clean    - Clean build artifacts"
	@echo "  make proto    - Compile protobuf files"
	@echo "  make check    - Run cargo check"
	@echo "  make fmt      - Format code"
	@echo "  make clippy   - Run clippy linter"

build:
	cargo build --release

test:
	cargo test --all

run:
	cargo run

clean:
	cargo clean

proto:
	@echo "Protobuf compilation is handled by build.rs"
	cargo build

check:
	cargo check

fmt:
	cargo fmt

clippy:
	cargo clippy -- -D warnings

# Run specific test suites
test-property:
	cargo test --test exponential_backoff_property_test
	cargo test --test circuit_breaker_property_test
	cargo test --test dlq_property_test

test-unit:
	cargo test --test retry_scenarios_test

# Development
dev:
	cargo watch -x run

# Docker (future)
docker-build:
	docker build -t retry-engine:latest .

docker-run:
	docker run -p 8450:8450 retry-engine:latest
