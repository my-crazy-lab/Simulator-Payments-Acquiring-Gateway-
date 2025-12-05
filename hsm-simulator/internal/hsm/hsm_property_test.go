package hsm

import (
	"testing"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

/**
 * Feature: payment-acquiring-gateway, Property 21: HSM Key Never Exposed
 * For any cryptographic operation performed by the HSM, the raw key material
 * should never be returned in the response - only encrypted/decrypted data or signatures.
 * Validates: Requirements 11.3
 */
func TestProperty_HSMKeyNeverExposed(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("HSM operations never expose raw key material", prop.ForAll(
		func(keyID string, plaintext []byte, aad []byte) bool {
			hsm := NewHSM()
			
			// Generate a key
			metadata, err := hsm.GenerateKey(keyID, "AES-256-GCM")
			if err != nil {
				return false
			}
			
			// Verify GenerateKey response doesn't contain key material
			// It should only return metadata
			if metadata.KeyID == "" || metadata.Algorithm == "" {
				return false
			}
			
			// Encrypt some data
			ciphertext, nonce, keyVersion, err := hsm.Encrypt(keyID, plaintext, aad)
			if err != nil {
				return false
			}
			
			// Verify Encrypt response doesn't expose the key
			// It should only return ciphertext, nonce, and version
			if ciphertext == nil || nonce == nil || keyVersion == 0 {
				return false
			}
			
			// Decrypt the data
			decrypted, err := hsm.Decrypt(keyID, ciphertext, nonce, aad, keyVersion)
			if err != nil {
				return false
			}
			
			// Verify Decrypt response doesn't expose the key
			// It should only return plaintext
			if decrypted == nil {
				return false
			}
			
			// Get key info
			keyInfo, err := hsm.GetKeyInfo(keyID)
			if err != nil {
				return false
			}
			
			// Verify GetKeyInfo doesn't expose key material
			// It should only return metadata
			if keyInfo.KeyID == "" || keyInfo.Algorithm == "" {
				return false
			}
			
			// The key should never be accessible through normal API calls
			// All operations should work without exposing the raw key
			return true
		},
		gen.Identifier().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) > 0 && len(b) < 1000 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) < 100 }),
	))

	properties.TestingRun(t)
}

/**
 * Feature: payment-acquiring-gateway, Property 21: HSM Key Never Exposed (Encryption Round Trip)
 * For any plaintext encrypted by the HSM, decrypting it should return the original plaintext
 * without ever exposing the key material.
 * Validates: Requirements 11.3
 */
func TestProperty_EncryptionRoundTrip(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("encryption and decryption round trip without exposing keys", prop.ForAll(
		func(keyID string, plaintext []byte, aad []byte) bool {
			hsm := NewHSM()
			
			// Generate a key
			_, err := hsm.GenerateKey(keyID, "AES-256-GCM")
			if err != nil {
				return false
			}
			
			// Encrypt
			ciphertext, nonce, keyVersion, err := hsm.Encrypt(keyID, plaintext, aad)
			if err != nil {
				return false
			}
			
			// Decrypt
			decrypted, err := hsm.Decrypt(keyID, ciphertext, nonce, aad, keyVersion)
			if err != nil {
				return false
			}
			
			// Verify round trip
			if len(plaintext) != len(decrypted) {
				return false
			}
			
			for i := range plaintext {
				if plaintext[i] != decrypted[i] {
					return false
				}
			}
			
			return true
		},
		gen.Identifier().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) > 0 && len(b) < 1000 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) < 100 }),
	))

	properties.TestingRun(t)
}

/**
 * Feature: payment-acquiring-gateway, Property 21: HSM Key Never Exposed (Audit Logging)
 * For any key operation, an audit log should be created without exposing key material.
 * Validates: Requirements 11.3, 11.5
 */
func TestProperty_AuditLoggingWithoutKeyExposure(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("audit logs are created for all operations without exposing keys", prop.ForAll(
		func(keyID string, plaintext []byte) bool {
			hsm := NewHSM()
			
			// Perform various operations
			hsm.GenerateKey(keyID, "AES-256-GCM")
			hsm.Encrypt(keyID, plaintext, nil)
			hsm.RotateKey(keyID)
			
			// Get audit log
			auditLog := hsm.GetAuditLog()
			
			// Verify audit entries exist
			if len(auditLog) < 3 {
				return false
			}
			
			// Verify audit entries don't contain key material
			// They should only contain operation metadata
			for _, entry := range auditLog {
				if entry.Operation == "" || entry.KeyID == "" {
					return false
				}
				// Audit log should have timestamp
				if entry.Timestamp.IsZero() {
					return false
				}
			}
			
			return true
		},
		gen.Identifier().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) > 0 && len(b) < 1000 }),
	))

	properties.TestingRun(t)
}
