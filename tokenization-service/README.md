# Tokenization Service

The Tokenization Service is a PCI DSS compliant service that provides secure card PAN tokenization using format-preserving encryption. It integrates with the HSM Simulator for cryptographic key management.

## Features

- **Format-Preserving Tokenization**: Generates tokens that maintain the format of the original PAN
- **HSM Integration**: Uses Hardware Security Module for secure key management
- **Token Lifecycle Management**: Supports token expiration and revocation
- **Luhn Validation**: Validates card numbers using the Luhn checksum algorithm
- **Card Brand Detection**: Automatically detects card brands (Visa, Mastercard, Amex, Discover)
- **Secure Storage**: Encrypted PAN-to-token mappings with SHA-256 hashing

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Tokenization Service (Port 8445)            │
│                         [PCI SCOPE]                          │
├─────────────────────────────────────────────────────────────┤
│  gRPC API:                                                   │
│  - TokenizeCard(PAN, Expiry, CVV) → Token                  │
│  - DetokenizeCard(Token) → PAN, Expiry                     │
│  - ValidateToken(Token) → Valid/Invalid                    │
└─────────────────────────────────────────────────────────────┘
                              ↓
                    ┌─────────────────┐
                    │  HSM Simulator  │
                    │   (Port 8444)   │
                    │  AES-256-GCM    │
                    └─────────────────┘
```

## Requirements

- Go 1.21 or later
- HSM Simulator running on port 8444
- Protocol Buffers compiler (protoc)

## Installation

1. Install dependencies:
```bash
go mod download
```

2. Generate protobuf code:
```bash
make proto
```

3. Build the service:
```bash
make build
```

## Running the Service

### Start HSM Simulator First

```bash
cd ../hsm-simulator
go run cmd/server/main.go
```

### Start Tokenization Service

```bash
make run
# or
./bin/tokenization-server
```

The service will listen on port 8445.

## Testing

### Run All Tests

```bash
make test
```

### Run Property-Based Tests Only

```bash
make test-property
```

### Property Tests Implemented

#### Property 1: Tokenization Round Trip
**Validates: Requirements 2.4**

For any valid PAN and expiry date, tokenizing then detokenizing should return the original PAN and expiry values unchanged.

```bash
go test -v ./internal/tokenization/ -run TestProperty_TokenizationRoundTrip
```

#### Property 4: Token Uniqueness
**Validates: Requirements 2.1**

For any set of distinct PANs, the generated tokens must all be unique - no two different PANs should produce the same token.

```bash
go test -v ./internal/tokenization/ -run TestProperty_TokenUniqueness
```

#### Property 5: Invalid Token Rejection
**Validates: Requirements 2.5**

For any malformed or non-existent token, detokenization requests should be rejected with an error.

```bash
go test -v ./internal/tokenization/ -run TestProperty_InvalidTokenRejection
```

#### Property 3: Encryption Uses AES-256-GCM
**Validates: Requirements 1.2, 2.3**

For any sensitive data encrypted by the system, the encryption algorithm should be AES-256-GCM and decryption should successfully recover the original data.

```bash
go test -v ./internal/tokenization/ -run TestProperty_EncryptionAlgorithm
```

## API Usage

### TokenizeCard

```go
import (
    "context"
    "google.golang.org/grpc"
    pb "github.com/paymentgateway/tokenization-service/proto"
)

conn, _ := grpc.Dial("localhost:8445", grpc.WithInsecure())
client := pb.NewTokenizationServiceClient(conn)

req := &pb.TokenizeRequest{
    Pan:         "4532015112830366",
    ExpiryMonth: 12,
    ExpiryYear:  2025,
    Cvv:         "123",
}

resp, err := client.TokenizeCard(context.Background(), req)
// resp.Token: "9123456789010366"
// resp.LastFour: "0366"
// resp.CardBrand: "VISA"
```

### DetokenizeCard

```go
req := &pb.DetokenizeRequest{
    Token: "9123456789010366",
}

resp, err := client.DetokenizeCard(context.Background(), req)
// resp.Pan: "4532015112830366"
// resp.ExpiryMonth: 12
// resp.ExpiryYear: 2025
```

### ValidateToken

```go
req := &pb.ValidateRequest{
    Token: "9123456789010366",
}

resp, err := client.ValidateToken(context.Background(), req)
// resp.Valid: true
```

## Token Format

Tokens are format-preserving and follow this structure:
- First digit: Always `9` (indicates token, not real PAN)
- Middle digits: Random digits
- Last 4 digits: Same as original PAN (for display purposes)
- Length: Same as original PAN (13-19 digits)

Example:
- Original PAN: `4532015112830366` (16 digits)
- Generated Token: `9123456789010366` (16 digits)

## Security Features

### PCI DSS Compliance

1. **No Raw PAN Storage**: PANs are encrypted before storage
2. **HSM Key Management**: All encryption keys managed by HSM
3. **Token Expiration**: Tokens have configurable TTL (default: 1 year)
4. **Token Revocation**: Tokens can be revoked when compromised
5. **Audit Logging**: All operations logged (via HSM)

### Encryption

- Algorithm: AES-256-GCM
- Key Management: HSM-based
- Additional Authenticated Data (AAD): Expiry date
- Nonce: Randomly generated per encryption

### Validation

- **Luhn Checksum**: All PANs validated using Luhn algorithm
- **Expiry Validation**: Ensures expiry dates are valid and not in the past
- **Token Format Validation**: Validates token structure before processing

## Configuration

Configuration is done via constants in `cmd/server/main.go`:

```go
const (
    port       = ":8445"           // Service port
    hsmAddress = "localhost:8444"  // HSM address
    keyID      = "tokenization-key-1" // HSM key ID
    tokenTTL   = 24 * time.Hour * 365 // Token TTL (1 year)
)
```

## Error Handling

The service returns gRPC errors for various failure scenarios:

- `ErrInvalidPAN`: Invalid PAN format or failed Luhn check
- `ErrInvalidExpiry`: Invalid or expired expiry date
- `ErrTokenNotFound`: Token does not exist or has been revoked
- `ErrTokenExpired`: Token has exceeded its TTL
- `ErrInvalidToken`: Malformed token format
- `ErrDuplicateToken`: Token collision (extremely rare)
- `ErrEncryptionFailed`: HSM encryption operation failed
- `ErrDecryptionFailed`: HSM decryption operation failed

## Performance

- **Tokenization**: ~1-2ms per operation (depends on HSM latency)
- **Detokenization**: ~1-2ms per operation
- **Validation**: <1ms per operation (in-memory check)
- **Concurrency**: Thread-safe with mutex-protected data structures

## Development

### Project Structure

```
tokenization-service/
├── cmd/
│   └── server/
│       └── main.go              # Service entry point
├── internal/
│   ├── hsm/
│   │   └── client.go            # HSM gRPC client
│   ├── server/
│   │   └── server.go            # gRPC server implementation
│   └── tokenization/
│       ├── tokenization.go      # Core tokenization logic
│       ├── tokenization_test.go # Unit tests
│       └── tokenization_property_test.go # Property-based tests
├── proto/
│   └── tokenization.proto       # gRPC service definition
├── Dockerfile                   # Docker build configuration
├── Makefile                     # Build automation
├── go.mod                       # Go module definition
└── README.md                    # This file
```

### Adding New Features

1. Update proto definition in `proto/tokenization.proto`
2. Regenerate proto code: `make proto`
3. Implement logic in `internal/tokenization/tokenization.go`
4. Add tests in `internal/tokenization/tokenization_test.go`
5. Add property tests if applicable
6. Update this README

## Troubleshooting

### HSM Connection Failed

```
Failed to connect to HSM: connection refused
```

**Solution**: Ensure HSM Simulator is running on port 8444

### Key Generation Failed

```
HSM generate key failed: key already exists
```

**Solution**: This is expected if the key was already created. The service will continue normally.

### Token Collision

```
duplicate token generated
```

**Solution**: This is extremely rare. The service will retry token generation automatically.

## Requirements Validation

This implementation validates the following requirements:

- ✅ **Requirement 2.1**: Unique token generation using format-preserving encryption
- ✅ **Requirement 2.2**: Encrypted PAN-to-token mapping storage
- ✅ **Requirement 2.3**: HSM-managed cryptographic keys
- ✅ **Requirement 2.4**: Detokenization returns original PAN
- ✅ **Requirement 2.5**: Invalid token rejection with logging
- ✅ **Requirement 1.2**: AES-256-GCM encryption
- ✅ **Requirement 1.3**: Field-level encryption for PAN

## License

Copyright © 2025 Payment Gateway. All rights reserved.
