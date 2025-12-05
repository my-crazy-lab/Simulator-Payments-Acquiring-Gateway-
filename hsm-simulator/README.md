# HSM Simulator Service

A Hardware Security Module (HSM) simulator for the Payment Acquiring Gateway. This service provides cryptographic operations including key generation, encryption, decryption, and key rotation using AES-256-GCM.

## Features

- **Cryptographic Key Generation**: Generates 256-bit keys using cryptographically secure random number generation
- **AES-256-GCM Encryption/Decryption**: Industry-standard authenticated encryption
- **Key Version Management**: Supports multiple versions of keys for rotation
- **Key Rotation**: Rotate keys while maintaining backward compatibility with old versions
- **Audit Logging**: Comprehensive logging of all key operations
- **Thread-Safe**: Concurrent access to keys is properly synchronized

## Security Principles

### Key Never Exposed (Property 21)
The HSM never exposes raw key material through any API. All operations (encrypt, decrypt, rotate) work with the keys internally without returning them to callers.

### Backward Compatibility (Property 22)
After key rotation, data encrypted with old key versions remains decryptable using the old version identifier. This ensures zero downtime during key rotation.

## API

### GenerateKey
Generates a new cryptographic key.

```go
metadata, err := hsm.GenerateKey("key-id", "AES-256-GCM")
```

Returns metadata about the key without exposing the key material.

### Encrypt
Encrypts plaintext using AES-256-GCM.

```go
ciphertext, nonce, keyVersion, err := hsm.Encrypt("key-id", plaintext, aad)
```

Returns:
- `ciphertext`: Encrypted data
- `nonce`: Random nonce used for encryption
- `keyVersion`: Version of the key used
- `err`: Error if operation failed

### Decrypt
Decrypts ciphertext using AES-256-GCM.

```go
plaintext, err := hsm.Decrypt("key-id", ciphertext, nonce, aad, keyVersion)
```

Uses the specified key version to decrypt data, enabling backward compatibility.

### RotateKey
Creates a new version of an existing key.

```go
newVersion, oldVersion, err := hsm.RotateKey("key-id")
```

After rotation:
- New encryptions use the new key version
- Old data remains decryptable with old versions

### GetKeyInfo
Returns metadata about a key without exposing key material.

```go
keyInfo, err := hsm.GetKeyInfo("key-id")
```

Returns:
- Key ID
- Algorithm
- Current version
- Available versions
- Creation and rotation timestamps

### GetAuditLog
Returns all audit log entries for compliance and troubleshooting.

```go
auditLog := hsm.GetAuditLog()
```

## Testing

The implementation includes comprehensive tests:

### Property-Based Tests (100+ iterations each)

**Property 21: HSM Key Never Exposed**
- Verifies that no API operation exposes raw key material
- Tests encryption, decryption, key info, and audit logging
- Validates: Requirements 11.3

**Property 22: Key Rotation Backward Compatibility**
- Verifies data encrypted with old versions remains decryptable after rotation
- Tests single and multiple rotations
- Validates new encryptions use new key versions
- Validates: Requirements 11.4

### Unit Tests

Edge cases covered:
- Invalid key IDs
- Non-existent keys
- Invalid algorithms
- Corrupted ciphertext/nonce
- Wrong AAD (Additional Authenticated Data)
- Invalid key versions
- Concurrent key access
- Duplicate key generation
- Empty plaintext
- Large plaintext (1MB+)
- Audit logging verification

## Running Tests

```bash
# Run all tests
go test ./internal/hsm/...

# Run property tests only
go test ./internal/hsm/ -run TestProperty

# Run unit tests only
go test ./internal/hsm/ -run Test -v

# Run with coverage
go test ./internal/hsm/ -cover
```

## Requirements Validation

This implementation validates the following requirements:

- **11.1**: Cryptographically secure random number generation for keys
- **11.3**: Keys never exposed through API operations
- **11.4**: Key rotation with backward compatibility
- **11.5**: Audit logging for all key operations

## Architecture

```
hsm-simulator/
├── cmd/
│   └── server/
│       └── main.go                 # Service entry point
├── internal/
│   └── hsm/
│       ├── hsm.go                  # Core HSM implementation
│       ├── hsm_test.go             # Unit tests
│       ├── hsm_property_test.go    # Property tests (Key Never Exposed)
│       └── key_rotation_property_test.go  # Property tests (Key Rotation)
├── proto/
│   └── hsm.proto                   # gRPC service definition
├── go.mod
└── README.md
```

## Future Enhancements

- gRPC server implementation
- Persistent key storage
- Hardware-backed key storage integration
- Key expiration and lifecycle management
- Support for additional algorithms (RSA, ECDSA)
- Distributed key management
- Key backup and recovery

## PCI DSS Compliance

This HSM simulator is designed for the PCI-scoped portion of the payment gateway:

- Keys are never exposed through APIs
- All operations are audited
- Cryptographically secure random generation
- Support for key rotation without downtime
- Thread-safe concurrent access

**Note**: This is a simulator for development and testing. Production deployments should use hardware HSMs or cloud HSM services (AWS CloudHSM, Azure Key Vault, etc.).
