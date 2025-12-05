.PHONY: test test-unit test-property test-coverage build run clean

# Run all tests
test:
	go test -v ./internal/hsm/...

# Run unit tests only
test-unit:
	go test -v ./internal/hsm/ -run '^Test[^P]'

# Run property tests only
test-property:
	go test -v ./internal/hsm/ -run 'TestProperty'

# Run tests with coverage
test-coverage:
	go test -v ./internal/hsm/ -coverprofile=coverage.out
	go tool cover -html=coverage.out -o coverage.html
	@echo "Coverage report generated: coverage.html"

# Build the service
build:
	go build -o bin/hsm-simulator ./cmd/server

# Run the service
run: build
	./bin/hsm-simulator

# Clean build artifacts
clean:
	rm -rf bin/
	rm -f coverage.out coverage.html

# Download dependencies
deps:
	go mod download
	go mod tidy

# Format code
fmt:
	go fmt ./...

# Run linter
lint:
	golangci-lint run ./...

# Run all checks (format, lint, test)
check: fmt lint test
