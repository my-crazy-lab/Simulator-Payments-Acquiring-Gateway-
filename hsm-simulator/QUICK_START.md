# HSM Simulator - Quick Start Guide

## Prerequisites

Install Go 1.21 or later:
```bash
# Download and install Go from https://golang.org/dl/
# Or use a package manager:
wget https://go.dev/dl/go1.21.0.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.21.0.linux-amd64.tar.gz
export PATH=$PATH:/usr/local/go/bin
```

## Run All Tests (Recommended)

```bash
cd hsm-simulator
./verify.sh
```

This will:
- ✅ Check Go installation
- ✅ Download dependencies
- ✅ Run all unit tests
- ✅ Run Property 21 tests (HSM Key Never Exposed)
- ✅ Run Property 22 tests (Key Rotation Backward Compatibility)
- ✅ Generate coverage report
- ✅ Build the service
- ✅ Display summary

## Run Tests Manually

```bash
# All tests
go test -v ./internal/hsm/...

# Property tests only (600+ test cases)
go test -v ./internal/hsm/ -run TestProperty

# Unit tests only
go test -v ./internal/hsm/ -run '^Test[^P]'

# With coverage
go test ./internal/hsm/ -coverprofile=coverage.out
go tool cover -html=coverage.out -o coverage.html
```

## Using Makefile

```bash
make test           # Run all tests
make test-property  # Property tests only
make test-unit      # Unit tests only
make test-coverage  # Generate coverage report
make build          # Build the service
make run            # Build and run
```

## Expected Output

### Property Tests
```
=== RUN   TestProperty_HSMKeyNeverExposed
+ HSM operations never expose raw key material: OK, passed 100 tests.
--- PASS: TestProperty_HSMKeyNeverExposed (0.15s)

=== RUN   TestProperty_KeyRotationBackwardCompatibility
+ data encrypted with old key version remains decryptable after rotation: OK, passed 100 tests.
--- PASS: TestProperty_KeyRotationBackwardCompatibility (0.20s)
```

### Unit Tests
```
=== RUN   TestInvalidKeyID
--- PASS: TestInvalidKeyID (0.00s)
=== RUN   TestEncryptionWithCorruptedData
--- PASS: TestEncryptionWithCorruptedData (0.00s)
=== RUN   TestConcurrentKeyAccess
--- PASS: TestConcurrentKeyAccess (0.05s)
...
PASS
ok      github.com/paymentgateway/hsm-simulator/internal/hsm   0.5s
```

## Troubleshooting

### "go: command not found"
Install Go from https://golang.org/doc/install

### "cannot find package"
Run: `go mod download && go mod tidy`

### Tests fail
Check `IMPLEMENTATION_STATUS.md` for expected behavior

## What Gets Tested

### Property 21: HSM Key Never Exposed (300+ test cases)
- Keys never returned in any API response
- Encryption/decryption works without exposing keys
- Audit logs don't contain key material

### Property 22: Key Rotation Backward Compatibility (300+ test cases)
- Old data decryptable after rotation
- Multiple rotations maintain compatibility
- New encryptions use new key versions

### Unit Tests (11 test functions)
- Invalid inputs (key IDs, algorithms, versions)
- Corrupted data handling
- Concurrent access (300 operations)
- Edge cases (empty data, large data)
- Audit logging verification

## Success Criteria

All tests should pass:
- ✅ 11 unit test functions
- ✅ 6 property test functions (100 iterations each)
- ✅ ~600+ total test cases
- ✅ Coverage > 80%

## Next Steps After Tests Pass

1. Review coverage report: `coverage.html`
2. Check audit logs are working
3. Verify concurrent access is safe
4. Integrate with tokenization service
5. Deploy to PCI-scoped environment
