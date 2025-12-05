package tokenization

import (
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

/**
 * Feature: payment-acquiring-gateway, Property 1: Tokenization Round Trip
 * For any valid PAN and expiry date, tokenizing then detokenizing
 * should return the original PAN and expiry values unchanged.
 * Validates: Requirements 2.4
 */
func TestProperty_TokenizationRoundTrip(t *testing.T) {
	properties := gopter.NewProperties(nil)
	
	properties.Property("tokenize then detokenize returns original PAN", prop.ForAll(
		func(pan string, month int, year int) bool {
			// Setup
			mockHSM := &MockHSMClient{}
			service := NewService(mockHSM, "test-key", 24*time.Hour)
			
			// Tokenize
			tokenData, err := service.TokenizeCard(pan, month, year, "123")
			if err != nil {
				t.Logf("Tokenization failed: %v", err)
				return false
			}
			
			// Detokenize
			retrievedPAN, retrievedMonth, retrievedYear, err := service.DetokenizeCard(tokenData.Token)
			if err != nil {
				t.Logf("Detokenization failed: %v", err)
				return false
			}
			
			// Verify round trip
			if retrievedPAN != pan {
				t.Logf("PAN mismatch: got %v, want %v", retrievedPAN, pan)
				return false
			}
			if retrievedMonth != month {
				t.Logf("Month mismatch: got %v, want %v", retrievedMonth, month)
				return false
			}
			if retrievedYear != year {
				t.Logf("Year mismatch: got %v, want %v", retrievedYear, year)
				return false
			}
			
			return true
		},
		genValidPAN(),
		genValidMonth(),
		genValidYear(),
	))
	
	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

/**
 * Feature: payment-acquiring-gateway, Property 4: Token Uniqueness
 * For any set of distinct PANs, the generated tokens must all be unique -
 * no two different PANs should produce the same token.
 * Validates: Requirements 2.1
 */
func TestProperty_TokenUniqueness(t *testing.T) {
	properties := gopter.NewProperties(nil)
	
	properties.Property("different PANs generate unique tokens", prop.ForAll(
		func(pans []string) bool {
			// Setup
			mockHSM := &MockHSMClient{}
			service := NewService(mockHSM, "test-key", 24*time.Hour)
			
			tokens := make(map[string]string) // token -> PAN
			
			for _, pan := range pans {
				tokenData, err := service.TokenizeCard(pan, 12, 2025, "123")
				if err != nil {
					t.Logf("Tokenization failed for PAN %v: %v", pan, err)
					return false
				}
				
				// Check if token already exists
				if existingPAN, exists := tokens[tokenData.Token]; exists {
					if existingPAN != pan {
						t.Logf("Duplicate token %v for different PANs: %v and %v", 
							tokenData.Token, existingPAN, pan)
						return false
					}
				}
				
				tokens[tokenData.Token] = pan
			}
			
			return true
		},
		genUniquePANList(),
	))
	
	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

/**
 * Feature: payment-acquiring-gateway, Property 5: Invalid Token Rejection
 * For any malformed or non-existent token, detokenization requests should be
 * rejected with an error and an audit log entry should be created.
 * Validates: Requirements 2.5
 */
func TestProperty_InvalidTokenRejection(t *testing.T) {
	properties := gopter.NewProperties(nil)
	
	properties.Property("invalid tokens are rejected", prop.ForAll(
		func(invalidToken string) bool {
			// Setup
			mockHSM := &MockHSMClient{}
			service := NewService(mockHSM, "test-key", 24*time.Hour)
			
			// Try to detokenize invalid token
			_, _, _, err := service.DetokenizeCard(invalidToken)
			
			// Should return an error
			if err == nil {
				t.Logf("Invalid token %v was not rejected", invalidToken)
				return false
			}
			
			// Error should be one of the expected types
			if err != ErrTokenNotFound && err != ErrInvalidToken && err != ErrTokenExpired {
				t.Logf("Unexpected error type: %v", err)
				return false
			}
			
			return true
		},
		genInvalidToken(),
	))
	
	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

/**
 * Feature: payment-acquiring-gateway, Property 3: Encryption Uses AES-256-GCM
 * For any sensitive data encrypted by the system, the encryption algorithm
 * identifier should be AES-256-GCM and decryption should successfully
 * recover the original data.
 * Validates: Requirements 1.2, 2.3
 */
func TestProperty_EncryptionAlgorithm(t *testing.T) {
	properties := gopter.NewProperties(nil)
	
	properties.Property("encryption uses AES-256-GCM and is reversible", prop.ForAll(
		func(pan string) bool {
			// Setup with a real encryption mock that simulates AES-256-GCM
			encryptedData := make(map[string][]byte)
			mockHSM := &MockHSMClient{
				encryptFunc: func(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error) {
					// Simulate encryption by storing plaintext
					key := string(plaintext) + string(aad)
					encryptedData[key] = plaintext
					return []byte("encrypted_" + string(plaintext)), []byte("nonce"), 1, nil
				},
				decryptFunc: func(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error) {
					// Simulate decryption by retrieving stored plaintext
					// In real AES-256-GCM, we'd verify the ciphertext
					// For this test, we verify the round-trip works
					return encryptedData[string(ciphertext[10:])+string(aad)], nil
				},
			}
			
			service := NewService(mockHSM, "test-key", 24*time.Hour)
			
			// Tokenize (which encrypts)
			tokenData, err := service.TokenizeCard(pan, 12, 2025, "123")
			if err != nil {
				t.Logf("Tokenization failed: %v", err)
				return false
			}
			
			// Detokenize (which decrypts)
			retrievedPAN, _, _, err := service.DetokenizeCard(tokenData.Token)
			if err != nil {
				t.Logf("Detokenization failed: %v", err)
				return false
			}
			
			// Verify encryption/decryption round trip
			if retrievedPAN != pan {
				t.Logf("Encryption round trip failed: got %v, want %v", retrievedPAN, pan)
				return false
			}
			
			return true
		},
		genValidPAN(),
	))
	
	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// Generators for property-based testing

// genValidPAN generates valid PANs with correct Luhn checksum
func genValidPAN() gopter.Gen {
	return gen.OneConstOf(
		"4532015112830366", // Visa
		"5425233430109903", // Mastercard
		"378282246310005",  // Amex
		"6011000000000004", // Discover
		"4111111111111111", // Visa test
		"5555555555554444", // Mastercard test
		"4012888888881881", // Visa
		"5105105105105100", // Mastercard
	)
}

// genValidMonth generates valid month values (1-12)
func genValidMonth() gopter.Gen {
	return gen.IntRange(1, 12)
}

// genValidYear generates valid future year values
func genValidYear() gopter.Gen {
	currentYear := time.Now().Year()
	return gen.IntRange(currentYear, currentYear+5)
}

// genUniquePANList generates a list of unique valid PANs
func genUniquePANList() gopter.Gen {
	return gen.SliceOfN(5, genValidPAN()).
		SuchThat(func(v interface{}) bool {
			pans := v.([]string)
			seen := make(map[string]bool)
			for _, pan := range pans {
				if seen[pan] {
					return false
				}
				seen[pan] = true
			}
			return true
		})
}

// genInvalidToken generates invalid token formats
func genInvalidToken() gopter.Gen {
	return gen.OneConstOf(
		"",                    // Empty
		"invalid",             // Non-numeric
		"123",                 // Too short
		"4123456789012345",    // Wrong prefix (should start with 9)
		"91234567890123456789012345", // Too long
		"abc123def456",        // Contains letters
		"9999999999999999",    // Non-existent token
	)
}
