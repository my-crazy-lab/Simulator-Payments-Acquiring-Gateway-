package tokenization

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"math/big"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

var (
	ErrInvalidPAN        = errors.New("invalid PAN format")
	ErrInvalidExpiry     = errors.New("invalid expiry date")
	ErrTokenNotFound     = errors.New("token not found")
	ErrTokenExpired      = errors.New("token expired")
	ErrInvalidToken      = errors.New("invalid token format")
	ErrDuplicateToken    = errors.New("duplicate token generated")
	ErrEncryptionFailed  = errors.New("encryption failed")
	ErrDecryptionFailed  = errors.New("decryption failed")
)

// HSMClient interface for HSM operations
type HSMClient interface {
	Encrypt(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error)
	Decrypt(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error)
}

// TokenData represents the encrypted token mapping
type TokenData struct {
	Token         string
	EncryptedPAN  []byte
	Nonce         []byte
	KeyVersion    int
	PANHash       string
	LastFour      string
	CardBrand     string
	ExpiryMonth   int
	ExpiryYear    int
	CreatedAt     time.Time
	ExpiresAt     time.Time
	IsActive      bool
	mu            sync.RWMutex
}

// Service provides tokenization operations
type Service struct {
	hsmClient     HSMClient
	keyID         string
	tokens        map[string]*TokenData  // token -> TokenData
	panHashIndex  map[string]string      // PANHash -> token
	mu            sync.RWMutex
	tokenTTL      time.Duration
}

// NewService creates a new tokenization service
func NewService(hsmClient HSMClient, keyID string, tokenTTL time.Duration) *Service {
	return &Service{
		hsmClient:    hsmClient,
		keyID:        keyID,
		tokens:       make(map[string]*TokenData),
		panHashIndex: make(map[string]string),
		tokenTTL:     tokenTTL,
	}
}

// TokenizeCard tokenizes a PAN using format-preserving encryption
func (s *Service) TokenizeCard(pan string, expiryMonth, expiryYear int, cvv string) (*TokenData, error) {
	// Validate PAN
	if err := validatePAN(pan); err != nil {
		return nil, err
	}
	
	// Validate expiry
	if err := validateExpiry(expiryMonth, expiryYear); err != nil {
		return nil, err
	}
	
	// Check if PAN already tokenized
	panHash := hashPAN(pan)
	s.mu.RLock()
	existingToken, exists := s.panHashIndex[panHash]
	s.mu.RUnlock()
	
	if exists {
		s.mu.RLock()
		tokenData := s.tokens[existingToken]
		s.mu.RUnlock()
		
		// Return existing token if still valid
		if tokenData.IsActive && time.Now().Before(tokenData.ExpiresAt) {
			return tokenData, nil
		}
	}
	
	// Encrypt PAN using HSM
	plaintext := []byte(pan)
	aad := []byte(fmt.Sprintf("%d-%d", expiryMonth, expiryYear))
	
	ciphertext, nonce, keyVersion, err := s.hsmClient.Encrypt(s.keyID, plaintext, aad)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrEncryptionFailed, err)
	}
	
	// Generate format-preserving token
	token, err := s.generateFormatPreservingToken(pan)
	if err != nil {
		return nil, err
	}
	
	// Ensure token uniqueness
	s.mu.Lock()
	defer s.mu.Unlock()
	
	if _, exists := s.tokens[token]; exists {
		return nil, ErrDuplicateToken
	}
	
	// Create token data
	now := time.Now()
	tokenData := &TokenData{
		Token:        token,
		EncryptedPAN: ciphertext,
		Nonce:        nonce,
		KeyVersion:   keyVersion,
		PANHash:      panHash,
		LastFour:     pan[len(pan)-4:],
		CardBrand:    detectCardBrand(pan),
		ExpiryMonth:  expiryMonth,
		ExpiryYear:   expiryYear,
		CreatedAt:    now,
		ExpiresAt:    now.Add(s.tokenTTL),
		IsActive:     true,
	}
	
	// Store token
	s.tokens[token] = tokenData
	s.panHashIndex[panHash] = token
	
	return tokenData, nil
}

// DetokenizeCard retrieves the original PAN from a token
func (s *Service) DetokenizeCard(token string) (pan string, expiryMonth, expiryYear int, err error) {
	// Validate token format
	if err := validateTokenFormat(token); err != nil {
		return "", 0, 0, err
	}
	
	// Retrieve token data
	s.mu.RLock()
	tokenData, exists := s.tokens[token]
	s.mu.RUnlock()
	
	if !exists {
		return "", 0, 0, ErrTokenNotFound
	}
	
	tokenData.mu.RLock()
	defer tokenData.mu.RUnlock()
	
	// Check if token is active
	if !tokenData.IsActive {
		return "", 0, 0, ErrTokenNotFound
	}
	
	// Check if token is expired
	if time.Now().After(tokenData.ExpiresAt) {
		return "", 0, 0, ErrTokenExpired
	}
	
	// Decrypt PAN using HSM
	aad := []byte(fmt.Sprintf("%d-%d", tokenData.ExpiryMonth, tokenData.ExpiryYear))
	plaintext, err := s.hsmClient.Decrypt(
		s.keyID,
		tokenData.EncryptedPAN,
		tokenData.Nonce,
		aad,
		tokenData.KeyVersion,
	)
	if err != nil {
		return "", 0, 0, fmt.Errorf("%w: %v", ErrDecryptionFailed, err)
	}
	
	return string(plaintext), tokenData.ExpiryMonth, tokenData.ExpiryYear, nil
}

// ValidateToken checks if a token is valid
func (s *Service) ValidateToken(token string) (bool, error) {
	if err := validateTokenFormat(token); err != nil {
		return false, err
	}
	
	s.mu.RLock()
	tokenData, exists := s.tokens[token]
	s.mu.RUnlock()
	
	if !exists {
		return false, ErrTokenNotFound
	}
	
	tokenData.mu.RLock()
	defer tokenData.mu.RUnlock()
	
	if !tokenData.IsActive {
		return false, nil
	}
	
	if time.Now().After(tokenData.ExpiresAt) {
		return false, nil
	}
	
	return true, nil
}

// RevokeToken revokes a token
func (s *Service) RevokeToken(token string) error {
	s.mu.RLock()
	tokenData, exists := s.tokens[token]
	s.mu.RUnlock()
	
	if !exists {
		return ErrTokenNotFound
	}
	
	tokenData.mu.Lock()
	defer tokenData.mu.Unlock()
	
	tokenData.IsActive = false
	return nil
}

// generateFormatPreservingToken generates a token that looks like a PAN
func (s *Service) generateFormatPreservingToken(pan string) (string, error) {
	// Keep first 6 digits (BIN) and last 4 digits for format preservation
	// Generate random middle digits
	panLen := len(pan)
	if panLen < 13 || panLen > 19 {
		return "", ErrInvalidPAN
	}
	
	// Token format: 9 + random(panLen-5) + last4
	// Using 9 as first digit to indicate it's a token (not a real card)
	var token strings.Builder
	token.WriteString("9")
	
	// Generate random middle digits
	middleLen := panLen - 5
	for i := 0; i < middleLen; i++ {
		digit, err := rand.Int(rand.Reader, big.NewInt(10))
		if err != nil {
			return "", fmt.Errorf("failed to generate random digit: %w", err)
		}
		token.WriteString(digit.String())
	}
	
	// Append last 4 digits
	token.WriteString(pan[len(pan)-4:])
	
	return token.String(), nil
}

// validatePAN validates PAN format and Luhn checksum
func validatePAN(pan string) error {
	// Remove spaces and dashes
	pan = strings.ReplaceAll(pan, " ", "")
	pan = strings.ReplaceAll(pan, "-", "")
	
	// Check if numeric
	if !regexp.MustCompile(`^\d+$`).MatchString(pan) {
		return ErrInvalidPAN
	}
	
	// Check length (13-19 digits)
	if len(pan) < 13 || len(pan) > 19 {
		return ErrInvalidPAN
	}
	
	// Validate Luhn checksum
	if !luhnCheck(pan) {
		return ErrInvalidPAN
	}
	
	return nil
}

// luhnCheck validates a number using the Luhn algorithm
func luhnCheck(number string) bool {
	var sum int
	parity := len(number) % 2
	
	for i, digit := range number {
		d, err := strconv.Atoi(string(digit))
		if err != nil {
			return false
		}
		
		if i%2 == parity {
			d *= 2
			if d > 9 {
				d -= 9
			}
		}
		
		sum += d
	}
	
	return sum%10 == 0
}

// validateExpiry validates expiry date
func validateExpiry(month, year int) error {
	if month < 1 || month > 12 {
		return ErrInvalidExpiry
	}
	
	now := time.Now()
	currentYear := now.Year()
	currentMonth := int(now.Month())
	
	if year < currentYear || (year == currentYear && month < currentMonth) {
		return ErrInvalidExpiry
	}
	
	// Reasonable future limit (10 years)
	if year > currentYear+10 {
		return ErrInvalidExpiry
	}
	
	return nil
}

// validateTokenFormat validates token format
func validateTokenFormat(token string) error {
	if token == "" {
		return ErrInvalidToken
	}
	
	// Token should be numeric and 13-19 digits
	if !regexp.MustCompile(`^\d{13,19}$`).MatchString(token) {
		return ErrInvalidToken
	}
	
	// Token should start with 9 (our convention)
	if !strings.HasPrefix(token, "9") {
		return ErrInvalidToken
	}
	
	return nil
}

// hashPAN creates a SHA-256 hash of the PAN for indexing
func hashPAN(pan string) string {
	hash := sha256.Sum256([]byte(pan))
	return hex.EncodeToString(hash[:])
}

// detectCardBrand detects the card brand from PAN
func detectCardBrand(pan string) string {
	if len(pan) < 2 {
		return "UNKNOWN"
	}
	
	// Visa: starts with 4
	if pan[0] == '4' {
		return "VISA"
	}
	
	// Mastercard: starts with 51-55 or 2221-2720
	if len(pan) >= 2 {
		prefix2, _ := strconv.Atoi(pan[:2])
		if prefix2 >= 51 && prefix2 <= 55 {
			return "MASTERCARD"
		}
	}
	if len(pan) >= 4 {
		prefix4, _ := strconv.Atoi(pan[:4])
		if prefix4 >= 2221 && prefix4 <= 2720 {
			return "MASTERCARD"
		}
	}
	
	// Amex: starts with 34 or 37
	if len(pan) >= 2 {
		prefix := pan[:2]
		if prefix == "34" || prefix == "37" {
			return "AMEX"
		}
	}
	
	// Discover: starts with 6011, 622126-622925, 644-649, 65
	if len(pan) >= 4 {
		prefix4 := pan[:4]
		if prefix4 == "6011" {
			return "DISCOVER"
		}
		prefix6, _ := strconv.Atoi(pan[:6])
		if prefix6 >= 622126 && prefix6 <= 622925 {
			return "DISCOVER"
		}
	}
	if len(pan) >= 3 {
		prefix3, _ := strconv.Atoi(pan[:3])
		if prefix3 >= 644 && prefix3 <= 649 {
			return "DISCOVER"
		}
	}
	if len(pan) >= 2 && pan[:2] == "65" {
		return "DISCOVER"
	}
	
	return "UNKNOWN"
}
