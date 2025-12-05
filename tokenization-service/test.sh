#!/bin/bash

# Tokenization Service Test Runner
# This script runs all tests using Docker

set -e

echo "========================================="
echo "Tokenization Service Tests"
echo "========================================="
echo ""

# Build and run tests in Docker
echo "🐳 Building Docker image with tests..."
docker build --target test -t tokenization-service-test . 2>&1 | tail -20

echo ""
echo "✓ Tests completed"
echo ""

# Run property tests specifically
echo "🔬 Running property-based tests..."
docker run --rm tokenization-service-test go test -v ./internal/tokenization/ -run Property

echo ""
echo "========================================="
echo "Test Summary"
echo "========================================="
echo "✓ All tests completed"
