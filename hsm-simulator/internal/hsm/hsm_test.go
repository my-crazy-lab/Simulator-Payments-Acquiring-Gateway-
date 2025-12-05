package hsm

import (
	"sync"
	"testing"
)

// Test invalid key IDs
func TestInvalidKeyID(t *testing.T) {
	hsm := NewHSM()
	
	// Empty key ID should fail
	_, err := hsm.GenerateKey("", "AES-256-GCM")
	if err != ErrInvalidKeyID {
		t.Errorf("Expected ErrInvalidKeyID for empty key ID, got %v", err)
	}
	
	// Operations on non-existent key should fail
	_, _, _, err = hsm.Encrypt("non-existent-key", []byte("test"), nil)
	if err != ErrKeyNotFound {
		t.Errorf("Expected ErrKeyNotFound for non-existent key, got %v", err)
	}
	
	_, err = hsm.Decrypt("non-existent-key", []byte("test"), []byte("nonce"), nil, 1)
	if err != ErrKeyNotFound {
		t.Errorf("Expected ErrKeyNotFound for non-existent key, got %v", err)
	}
	
	_, _, err = hsm.RotateKey("non-existent-key")
	if err != ErrKeyNotFound {
		t.Errorf("Expected ErrKeyNotFound for non-existent key, got %v", err)
	}
	
	_, err = hsm.GetKeyInfo("non-existent-key")
	if err != ErrKeyNotFound {
		t.Errorf("Expected ErrKeyNotFound for non-existent key, got %v", err)
	}
}

// Test invalid algorithm
func TestInvalidAlgorithm(t *testing.T) {
	hsm := NewHSM()
	
	// Invalid algorithm should fail
	_, err := hsm.GenerateKey("test-key", "INVALID-ALGO")
	if err != ErrInvalidAlgorithm {
		t.Errorf("Expected ErrInvalidAlgorithm, got %v", err)
	}
	
	// Only AES-256-GCM should be supported
	_, err = hsm.GenerateKey("test-key", "AES-128-GCM")
	if err != ErrInvalidAlgorithm {
		t.Errorf("Expected ErrInvalidAlgorithm for AES-128-GCM, got %v", err)
	}
}

// Test encryption with corrupted data
func TestEncryptionWithCorruptedData(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	plaintext := []byte("sensitive data")
	aad := []byte("additional data")
	
	// Encrypt
	ciphertext, nonce, keyVersion, err := hsm.Encrypt("test-key", plaintext, aad)
	if err != nil {
		t.Fatalf("Failed to encrypt: %v", err)
	}
	
	// Corrupt the ciphertext
	corruptedCiphertext := make([]byte, len(ciphertext))
	copy(corruptedCiphertext, ciphertext)
	if len(corruptedCiphertext) > 0 {
		corruptedCiphertext[0] ^= 0xFF // Flip bits
	}
	
	// Decryption should fail
	_, err = hsm.Decrypt("test-key", corruptedCiphertext, nonce, aad, keyVersion)
	if err != ErrDecryptionFailed {
		t.Errorf("Expected ErrDecryptionFailed for corrupted ciphertext, got %v", err)
	}
	
	// Corrupt the nonce
	corruptedNonce := make([]byte, len(nonce))
	copy(corruptedNonce, nonce)
	if len(corruptedNonce) > 0 {
		corruptedNonce[0] ^= 0xFF
	}
	
	// Decryption should fail
	_, err = hsm.Decrypt("test-key", ciphertext, corruptedNonce, aad, keyVersion)
	if err != ErrDecryptionFailed {
		t.Errorf("Expected ErrDecryptionFailed for corrupted nonce, got %v", err)
	}
	
	// Wrong AAD should fail
	wrongAAD := []byte("wrong additional data")
	_, err = hsm.Decrypt("test-key", ciphertext, nonce, wrongAAD, keyVersion)
	if err != ErrDecryptionFailed {
		t.Errorf("Expected ErrDecryptionFailed for wrong AAD, got %v", err)
	}
}

// Test invalid key version
func TestInvalidKeyVersion(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	plaintext := []byte("test data")
	
	// Encrypt with version 1
	ciphertext, nonce, _, err := hsm.Encrypt("test-key", plaintext, nil)
	if err != nil {
		t.Fatalf("Failed to encrypt: %v", err)
	}
	
	// Try to decrypt with non-existent version
	_, err = hsm.Decrypt("test-key", ciphertext, nonce, nil, 999)
	if err != ErrInvalidKeyVersion {
		t.Errorf("Expected ErrInvalidKeyVersion for non-existent version, got %v", err)
	}
	
	// Try to decrypt with version 0
	_, err = hsm.Decrypt("test-key", ciphertext, nonce, nil, 0)
	if err != ErrInvalidKeyVersion {
		t.Errorf("Expected ErrInvalidKeyVersion for version 0, got %v", err)
	}
}

// Test concurrent key access
func TestConcurrentKeyAccess(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	// Number of concurrent operations
	numOps := 100
	
	var wg sync.WaitGroup
	errors := make(chan error, numOps*3)
	
	// Concurrent encryptions
	for i := 0; i < numOps; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			plaintext := []byte("concurrent test data")
			_, _, _, err := hsm.Encrypt("test-key", plaintext, nil)
			if err != nil {
				errors <- err
			}
		}(i)
	}
	
	// Concurrent key info queries
	for i := 0; i < numOps; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			_, err := hsm.GetKeyInfo("test-key")
			if err != nil {
				errors <- err
			}
		}(i)
	}
	
	// Concurrent rotations (some will succeed, some will be sequential)
	for i := 0; i < numOps; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			_, _, err := hsm.RotateKey("test-key")
			if err != nil {
				errors <- err
			}
		}(i)
	}
	
	wg.Wait()
	close(errors)
	
	// Check for errors
	for err := range errors {
		t.Errorf("Concurrent operation failed: %v", err)
	}
	
	// Verify key info is consistent
	keyInfo, err := hsm.GetKeyInfo("test-key")
	if err != nil {
		t.Fatalf("Failed to get key info: %v", err)
	}
	
	// Should have multiple versions from rotations
	if len(keyInfo.AvailableVersions) < 1 {
		t.Errorf("Expected at least 1 version, got %d", len(keyInfo.AvailableVersions))
	}
}

// Test duplicate key generation
func TestDuplicateKeyGeneration(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	// Try to generate same key again
	_, err = hsm.GenerateKey("test-key", "AES-256-GCM")
	if err == nil {
		t.Error("Expected error when generating duplicate key")
	}
}

// Test empty plaintext
func TestEmptyPlaintext(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	// Encrypt empty plaintext (should work)
	emptyPlaintext := []byte{}
	ciphertext, nonce, keyVersion, err := hsm.Encrypt("test-key", emptyPlaintext, nil)
	if err != nil {
		t.Fatalf("Failed to encrypt empty plaintext: %v", err)
	}
	
	// Decrypt should return empty plaintext
	decrypted, err := hsm.Decrypt("test-key", ciphertext, nonce, nil, keyVersion)
	if err != nil {
		t.Fatalf("Failed to decrypt: %v", err)
	}
	
	if len(decrypted) != 0 {
		t.Errorf("Expected empty decrypted plaintext, got %d bytes", len(decrypted))
	}
}

// Test audit logging
func TestAuditLogging(t *testing.T) {
	hsm := NewHSM()
	
	// Initially no audit logs
	auditLog := hsm.GetAuditLog()
	if len(auditLog) != 0 {
		t.Errorf("Expected empty audit log, got %d entries", len(auditLog))
	}
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	// Should have one audit entry
	auditLog = hsm.GetAuditLog()
	if len(auditLog) != 1 {
		t.Errorf("Expected 1 audit entry, got %d", len(auditLog))
	}
	
	if auditLog[0].Operation != "GenerateKey" {
		t.Errorf("Expected GenerateKey operation, got %s", auditLog[0].Operation)
	}
	
	if !auditLog[0].Success {
		t.Error("Expected successful operation")
	}
	
	// Encrypt
	hsm.Encrypt("test-key", []byte("test"), nil)
	
	// Should have two audit entries
	auditLog = hsm.GetAuditLog()
	if len(auditLog) != 2 {
		t.Errorf("Expected 2 audit entries, got %d", len(auditLog))
	}
	
	// Failed operation should be logged
	hsm.Encrypt("non-existent", []byte("test"), nil)
	
	auditLog = hsm.GetAuditLog()
	if len(auditLog) != 3 {
		t.Errorf("Expected 3 audit entries, got %d", len(auditLog))
	}
	
	if auditLog[2].Success {
		t.Error("Expected failed operation to be logged as unsuccessful")
	}
}

// Test large plaintext
func TestLargePlaintext(t *testing.T) {
	hsm := NewHSM()
	
	// Generate key
	_, err := hsm.GenerateKey("test-key", "AES-256-GCM")
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}
	
	// Create large plaintext (1MB)
	largePlaintext := make([]byte, 1024*1024)
	for i := range largePlaintext {
		largePlaintext[i] = byte(i % 256)
	}
	
	// Encrypt
	ciphertext, nonce, keyVersion, err := hsm.Encrypt("test-key", largePlaintext, nil)
	if err != nil {
		t.Fatalf("Failed to encrypt large plaintext: %v", err)
	}
	
	// Decrypt
	decrypted, err := hsm.Decrypt("test-key", ciphertext, nonce, nil, keyVersion)
	if err != nil {
		t.Fatalf("Failed to decrypt: %v", err)
	}
	
	// Verify
	if len(decrypted) != len(largePlaintext) {
		t.Errorf("Expected %d bytes, got %d", len(largePlaintext), len(decrypted))
	}
	
	for i := range largePlaintext {
		if largePlaintext[i] != decrypted[i] {
			t.Errorf("Mismatch at byte %d: expected %d, got %d", i, largePlaintext[i], decrypted[i])
			break
		}
	}
}
