package hsm

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"sync"
	"time"
)

var (
	ErrKeyNotFound      = errors.New("key not found")
	ErrInvalidKeyID     = errors.New("invalid key ID")
	ErrInvalidAlgorithm = errors.New("invalid algorithm")
	ErrDecryptionFailed = errors.New("decryption failed")
	ErrInvalidKeyVersion = errors.New("invalid key version")
)

// KeyVersion represents a specific version of a cryptographic key
type KeyVersion struct {
	Version   int
	KeyData   []byte
	CreatedAt time.Time
}

// KeyMetadata stores information about a key without exposing the key material
type KeyMetadata struct {
	KeyID            string
	Algorithm        string
	CurrentVersion   int
	AvailableVersions []int
	CreatedAt        time.Time
	LastRotatedAt    time.Time
}

// Key represents a cryptographic key with version management
type Key struct {
	ID        string
	Algorithm string
	Versions  map[int]*KeyVersion
	CurrentVersion int
	CreatedAt time.Time
	LastRotatedAt time.Time
	mu        sync.RWMutex
}

// HSM represents a Hardware Security Module simulator
type HSM struct {
	keys      map[string]*Key
	mu        sync.RWMutex
	auditLog  []AuditEntry
	auditMu   sync.Mutex
}

// AuditEntry represents a log entry for key operations
type AuditEntry struct {
	Timestamp time.Time
	Operation string
	KeyID     string
	Version   int
	Success   bool
	Error     string
}

// NewHSM creates a new HSM instance
func NewHSM() *HSM {
	return &HSM{
		keys:     make(map[string]*Key),
		auditLog: make([]AuditEntry, 0),
	}
}

// GenerateKey generates a new cryptographic key
func (h *HSM) GenerateKey(keyID, algorithm string) (*KeyMetadata, error) {
	if keyID == "" {
		return nil, ErrInvalidKeyID
	}
	
	if algorithm != "AES-256-GCM" {
		return nil, ErrInvalidAlgorithm
	}
	
	h.mu.Lock()
	defer h.mu.Unlock()
	
	// Check if key already exists
	if _, exists := h.keys[keyID]; exists {
		h.logAudit("GenerateKey", keyID, 0, false, "key already exists")
		return nil, fmt.Errorf("key %s already exists", keyID)
	}
	
	// Generate 256-bit (32-byte) key using cryptographically secure random
	keyData := make([]byte, 32)
	if _, err := io.ReadFull(rand.Reader, keyData); err != nil {
		h.logAudit("GenerateKey", keyID, 0, false, err.Error())
		return nil, fmt.Errorf("failed to generate random key: %w", err)
	}
	
	now := time.Now()
	key := &Key{
		ID:             keyID,
		Algorithm:      algorithm,
		Versions:       make(map[int]*KeyVersion),
		CurrentVersion: 1,
		CreatedAt:      now,
		LastRotatedAt:  now,
	}
	
	key.Versions[1] = &KeyVersion{
		Version:   1,
		KeyData:   keyData,
		CreatedAt: now,
	}
	
	h.keys[keyID] = key
	h.logAudit("GenerateKey", keyID, 1, true, "")
	
	return &KeyMetadata{
		KeyID:             keyID,
		Algorithm:         algorithm,
		CurrentVersion:    1,
		AvailableVersions: []int{1},
		CreatedAt:         now,
		LastRotatedAt:     now,
	}, nil
}

// Encrypt encrypts plaintext using AES-256-GCM
func (h *HSM) Encrypt(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error) {
	h.mu.RLock()
	key, exists := h.keys[keyID]
	h.mu.RUnlock()
	
	if !exists {
		h.logAudit("Encrypt", keyID, 0, false, "key not found")
		return nil, nil, 0, ErrKeyNotFound
	}
	
	key.mu.RLock()
	currentVersion := key.CurrentVersion
	keyVersion = currentVersion
	keyData := key.Versions[currentVersion].KeyData
	key.mu.RUnlock()
	
	// Create AES cipher
	block, err := aes.NewCipher(keyData)
	if err != nil {
		h.logAudit("Encrypt", keyID, keyVersion, false, err.Error())
		return nil, nil, 0, fmt.Errorf("failed to create cipher: %w", err)
	}
	
	// Create GCM mode
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		h.logAudit("Encrypt", keyID, keyVersion, false, err.Error())
		return nil, nil, 0, fmt.Errorf("failed to create GCM: %w", err)
	}
	
	// Generate nonce
	nonce = make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		h.logAudit("Encrypt", keyID, keyVersion, false, err.Error())
		return nil, nil, 0, fmt.Errorf("failed to generate nonce: %w", err)
	}
	
	// Encrypt
	ciphertext = gcm.Seal(nil, nonce, plaintext, aad)
	
	h.logAudit("Encrypt", keyID, keyVersion, true, "")
	return ciphertext, nonce, keyVersion, nil
}

// Decrypt decrypts ciphertext using AES-256-GCM
func (h *HSM) Decrypt(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error) {
	h.mu.RLock()
	key, exists := h.keys[keyID]
	h.mu.RUnlock()
	
	if !exists {
		h.logAudit("Decrypt", keyID, keyVersion, false, "key not found")
		return nil, ErrKeyNotFound
	}
	
	key.mu.RLock()
	version, versionExists := key.Versions[keyVersion]
	key.mu.RUnlock()
	
	if !versionExists {
		h.logAudit("Decrypt", keyID, keyVersion, false, "key version not found")
		return nil, ErrInvalidKeyVersion
	}
	
	// Create AES cipher
	block, err := aes.NewCipher(version.KeyData)
	if err != nil {
		h.logAudit("Decrypt", keyID, keyVersion, false, err.Error())
		return nil, fmt.Errorf("failed to create cipher: %w", err)
	}
	
	// Create GCM mode
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		h.logAudit("Decrypt", keyID, keyVersion, false, err.Error())
		return nil, fmt.Errorf("failed to create GCM: %w", err)
	}
	
	// Decrypt
	plaintext, err := gcm.Open(nil, nonce, ciphertext, aad)
	if err != nil {
		h.logAudit("Decrypt", keyID, keyVersion, false, "decryption failed")
		return nil, ErrDecryptionFailed
	}
	
	h.logAudit("Decrypt", keyID, keyVersion, true, "")
	return plaintext, nil
}

// RotateKey creates a new version of an existing key
func (h *HSM) RotateKey(keyID string) (newVersion, oldVersion int, err error) {
	h.mu.RLock()
	key, exists := h.keys[keyID]
	h.mu.RUnlock()
	
	if !exists {
		h.logAudit("RotateKey", keyID, 0, false, "key not found")
		return 0, 0, ErrKeyNotFound
	}
	
	key.mu.Lock()
	defer key.mu.Unlock()
	
	// Generate new key data
	keyData := make([]byte, 32)
	if _, err := io.ReadFull(rand.Reader, keyData); err != nil {
		h.logAudit("RotateKey", keyID, 0, false, err.Error())
		return 0, 0, fmt.Errorf("failed to generate random key: %w", err)
	}
	
	oldVersion = key.CurrentVersion
	newVersion = oldVersion + 1
	
	key.Versions[newVersion] = &KeyVersion{
		Version:   newVersion,
		KeyData:   keyData,
		CreatedAt: time.Now(),
	}
	
	key.CurrentVersion = newVersion
	key.LastRotatedAt = time.Now()
	
	h.logAudit("RotateKey", keyID, newVersion, true, "")
	return newVersion, oldVersion, nil
}

// GetKeyInfo returns metadata about a key without exposing the key material
func (h *HSM) GetKeyInfo(keyID string) (*KeyMetadata, error) {
	h.mu.RLock()
	key, exists := h.keys[keyID]
	h.mu.RUnlock()
	
	if !exists {
		return nil, ErrKeyNotFound
	}
	
	key.mu.RLock()
	defer key.mu.RUnlock()
	
	versions := make([]int, 0, len(key.Versions))
	for v := range key.Versions {
		versions = append(versions, v)
	}
	
	return &KeyMetadata{
		KeyID:             key.ID,
		Algorithm:         key.Algorithm,
		CurrentVersion:    key.CurrentVersion,
		AvailableVersions: versions,
		CreatedAt:         key.CreatedAt,
		LastRotatedAt:     key.LastRotatedAt,
	}, nil
}

// GetAuditLog returns all audit log entries
func (h *HSM) GetAuditLog() []AuditEntry {
	h.auditMu.Lock()
	defer h.auditMu.Unlock()
	
	// Return a copy to prevent external modification
	logCopy := make([]AuditEntry, len(h.auditLog))
	copy(logCopy, h.auditLog)
	return logCopy
}

// logAudit adds an entry to the audit log
func (h *HSM) logAudit(operation, keyID string, version int, success bool, errorMsg string) {
	h.auditMu.Lock()
	defer h.auditMu.Unlock()
	
	entry := AuditEntry{
		Timestamp: time.Now(),
		Operation: operation,
		KeyID:     keyID,
		Version:   version,
		Success:   success,
		Error:     errorMsg,
	}
	
	h.auditLog = append(h.auditLog, entry)
}

// ExportKeyForTesting exports key data for testing purposes only
// This violates the security principle and should NEVER be used in production
func (h *HSM) ExportKeyForTesting(keyID string, version int) (string, error) {
	h.mu.RLock()
	key, exists := h.keys[keyID]
	h.mu.RUnlock()
	
	if !exists {
		return "", ErrKeyNotFound
	}
	
	key.mu.RLock()
	defer key.mu.RUnlock()
	
	keyVersion, versionExists := key.Versions[version]
	if !versionExists {
		return "", ErrInvalidKeyVersion
	}
	
	return hex.EncodeToString(keyVersion.KeyData), nil
}
