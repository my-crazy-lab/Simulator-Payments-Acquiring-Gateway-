#!/bin/bash

# HSM Simulator Verification Script
# This script verifies the HSM implementation by running all tests

set -e

echo "========================================="
echo "HSM Simulator Verification"
echo "========================================="
echo ""

# Check if Go is installed
if ! command -v go &> /dev/null; then
    echo "❌ Go is not installed. Please install Go 1.21 or later."
    echo "   Visit: https://golang.org/doc/install"
    exit 1
fi

echo "✓ Go is installed: $(go version)"
echo ""

# Check Go version
GO_VERSION=$(go version | awk '{print $3}' | sed 's/go//')
REQUIRED_VERSION="1.21"

if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$GO_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then
    echo "❌ Go version $GO_VERSION is too old. Required: $REQUIRED_VERSION or later"
    exit 1
fi

echo "✓ Go version is compatible"
echo ""

# Download dependencies
echo "📦 Downloading dependencies..."
go mod download
go mod tidy
echo "✓ Dependencies downloaded"
echo ""

# Run unit tests
echo "🧪 Running unit tests..."
echo "========================================="
go test -v ./internal/hsm/ -run '^Test[^P]' | tee unit-test-results.txt
UNIT_EXIT_CODE=${PIPESTATUS[0]}
echo ""

if [ $UNIT_EXIT_CODE -eq 0 ]; then
    echo "✓ All unit tests passed"
else
    echo "❌ Some unit tests failed"
fi
echo ""

# Run property tests for Property 21
echo "🔬 Running Property 21 tests (HSM Key Never Exposed)..."
echo "========================================="
go test -v ./internal/hsm/ -run 'TestProperty_HSMKeyNeverExposed|TestProperty_EncryptionRoundTrip|TestProperty_AuditLoggingWithoutKeyExposure' | tee property21-test-results.txt
PROP21_EXIT_CODE=${PIPESTATUS[0]}
echo ""

if [ $PROP21_EXIT_CODE -eq 0 ]; then
    echo "✓ Property 21 tests passed (100+ iterations)"
else
    echo "❌ Property 21 tests failed"
fi
echo ""

# Run property tests for Property 22
echo "🔬 Running Property 22 tests (Key Rotation Backward Compatibility)..."
echo "========================================="
go test -v ./internal/hsm/ -run 'TestProperty_KeyRotation' | tee property22-test-results.txt
PROP22_EXIT_CODE=${PIPESTATUS[0]}
echo ""

if [ $PROP22_EXIT_CODE -eq 0 ]; then
    echo "✓ Property 22 tests passed (100+ iterations)"
else
    echo "❌ Property 22 tests failed"
fi
echo ""

# Generate coverage report
echo "📊 Generating coverage report..."
go test ./internal/hsm/ -coverprofile=coverage.out -covermode=atomic
go tool cover -html=coverage.out -o coverage.html
COVERAGE=$(go tool cover -func=coverage.out | grep total | awk '{print $3}')
echo "✓ Coverage: $COVERAGE"
echo "✓ Coverage report: coverage.html"
echo ""

# Build the service
echo "🔨 Building HSM Simulator service..."
go build -o bin/hsm-simulator ./cmd/server
echo "✓ Build successful: bin/hsm-simulator"
echo ""

# Summary
echo "========================================="
echo "Verification Summary"
echo "========================================="
echo ""

TOTAL_FAILURES=0

if [ $UNIT_EXIT_CODE -eq 0 ]; then
    echo "✓ Unit Tests: PASSED"
else
    echo "❌ Unit Tests: FAILED"
    TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
fi

if [ $PROP21_EXIT_CODE -eq 0 ]; then
    echo "✓ Property 21 (Key Never Exposed): PASSED"
else
    echo "❌ Property 21 (Key Never Exposed): FAILED"
    TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
fi

if [ $PROP22_EXIT_CODE -eq 0 ]; then
    echo "✓ Property 22 (Key Rotation): PASSED"
else
    echo "❌ Property 22 (Key Rotation): FAILED"
    TOTAL_FAILURES=$((TOTAL_FAILURES + 1))
fi

echo ""
echo "Coverage: $COVERAGE"
echo ""

if [ $TOTAL_FAILURES -eq 0 ]; then
    echo "🎉 All verifications passed!"
    echo ""
    echo "Requirements validated:"
    echo "  ✓ 11.1 - Cryptographically secure random key generation"
    echo "  ✓ 11.3 - Keys never exposed through API"
    echo "  ✓ 11.4 - Key rotation with backward compatibility"
    echo "  ✓ 11.5 - Audit logging for all operations"
    exit 0
else
    echo "❌ $TOTAL_FAILURES verification(s) failed"
    exit 1
fi
