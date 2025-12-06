# Load Testing Makefile for Payment Acquiring Gateway
# Requirements: 27.1, 27.2, 27.3

.PHONY: help smoke load stress soak clean results

# Default target
help:
	@echo "Payment Gateway Load Testing"
	@echo ""
	@echo "Usage:"
	@echo "  make smoke    - Quick smoke test (10 VUs, 1 minute)"
	@echo "  make load     - Standard load test (10K TPS target)"
	@echo "  make stress   - Stress test to find breaking point"
	@echo "  make soak     - 24-hour soak test"
	@echo "  make results  - View latest test results"
	@echo "  make clean    - Clean up test results"
	@echo ""
	@echo "Environment variables:"
	@echo "  BASE_URL  - Gateway URL (default: http://localhost:8446)"
	@echo "  API_KEY   - API key for authentication"

# Configuration
BASE_URL ?= http://localhost:8446
API_KEY ?= pk_test_loadtest123456789012

# Create results directory
results:
	@mkdir -p results

# Smoke test - quick validation
smoke: results
	@echo "Running smoke test..."
	k6 run \
		--vus 10 \
		--duration 1m \
		--env BASE_URL=$(BASE_URL) \
		--env API_KEY=$(API_KEY) \
		--out json=results/smoke-$$(date +%Y%m%d-%H%M%S).json \
		k6-payment-load-test.js

# Standard load test - 10K TPS target
load: results
	@echo "Running standard load test (10K TPS target)..."
	k6 run \
		--env BASE_URL=$(BASE_URL) \
		--env API_KEY=$(API_KEY) \
		--out json=results/load-$$(date +%Y%m%d-%H%M%S).json \
		k6-payment-load-test.js

# Stress test - find breaking point
stress: results
	@echo "Running stress test..."
	k6 run \
		--vus 500 \
		--duration 30m \
		--env BASE_URL=$(BASE_URL) \
		--env API_KEY=$(API_KEY) \
		--out json=results/stress-$$(date +%Y%m%d-%H%M%S).json \
		k6-payment-load-test.js

# Soak test - 24-hour endurance
soak: results
	@echo "Running 24-hour soak test..."
	k6 run \
		--vus 200 \
		--duration 24h \
		--env BASE_URL=$(BASE_URL) \
		--env API_KEY=$(API_KEY) \
		--out json=results/soak-$$(date +%Y%m%d-%H%M%S).json \
		k6-payment-load-test.js

# View latest results
view-results:
	@echo "Latest test results:"
	@ls -la results/ 2>/dev/null || echo "No results found"
	@echo ""
	@if [ -f results/summary.json ]; then \
		echo "Summary:"; \
		cat results/summary.json | head -50; \
	fi

# Clean up results
clean:
	@echo "Cleaning up test results..."
	rm -rf results/*.json
	@echo "Done"

# Docker-based load test (if k6 not installed locally)
docker-smoke:
	@echo "Running smoke test via Docker..."
	docker run --rm -i \
		-v $$(pwd):/scripts \
		-e BASE_URL=$(BASE_URL) \
		-e API_KEY=$(API_KEY) \
		grafana/k6 run \
		--vus 10 \
		--duration 1m \
		/scripts/k6-payment-load-test.js

docker-load:
	@echo "Running load test via Docker..."
	docker run --rm -i \
		-v $$(pwd):/scripts \
		-e BASE_URL=$(BASE_URL) \
		-e API_KEY=$(API_KEY) \
		grafana/k6 run \
		/scripts/k6-payment-load-test.js
