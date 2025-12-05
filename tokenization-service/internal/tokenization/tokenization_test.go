package tokenization

import (
	"testing"
	"time"
)

// MockHSMClient for testing
type MockHSMClient struct {
	encryptFunc func(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error)
	decryptFunc func(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error)
}

func (m *MockHSMClient) Encrypt(keyID string, plaintext, aad []byte) (ciphertext, nonce []byte, keyVersion int, err error) {
	if m.encryptFunc != nil {
		return m.encryptFunc(keyID, plaintext, aad)
	}
	// Default mock: return plaintext as ciphertext with dummy nonce
	return plaintext, []byte("nonce123"), 1, nil
}

func (m *MockHSMClient) Decrypt(keyID string, ciphertext, nonce, aad []byte, keyVersion int) ([]byte, error) {
	if m.decryptFunc != nil {
		return m.decryptFunc(keyID, ciphertext, nonce, aad, keyVersion)
	}
	// Default mock: return ciphertext as plaintext
	return ciphertext, nil
}

func TestValidatePAN(t *testing.T) {
	tests := []struct {
		name    string
		pan     string
		wantErr bool
	}{
		{"Valid Visa", "4532015112830366", false},
		{"Valid Mastercard", "5425233430109903", false},
		{"Invalid Luhn", "4532015112830367", true},
		{"Too Short", "123456", true},
		{"Too Long", "12345678901234567890", true},
		{"Non-numeric", "453201511283abcd", true},
		{"Empty", "", true},
	}
	
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := validatePAN(tt.pan)
			if (err != nil) != tt.wantErr {
				t.Errorf("validatePAN() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestLuhnCheck(t *testing.T) {
	tests := []struct {
		name   string
		number string
		want   bool
	}{
		{"Valid Visa", "4532015112830366", true},
		{"Valid Mastercard", "5425233430109903", true},
		{"Invalid", "4532015112830367", false},
		{"Valid Amex", "378282246310005", true},
	}
	
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := luhnCheck(tt.number); got != tt.want {
				t.Errorf("luhnCheck() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestValidateExpiry(t *testing.T) {
	now := time.Now()
	currentYear := now.Year()
	currentMonth := int(now.Month())
	
	tests := []struct {
		name    string
		month   int
		year    int
		wantErr bool
	}{
		{"Valid Future", 12, currentYear + 1, false},
		{"Valid Current Month", currentMonth, currentYear, false},
		{"Invalid Month 0", 0, currentYear, true},
		{"Invalid Month 13", 13, currentYear, true},
		{"Past Year", 12, currentYear - 1, true},
		{"Past Month", currentMonth - 1, currentYear, true},
		{"Too Far Future", 12, currentYear + 11, true},
	}
	
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := validateExpiry(tt.month, tt.year)
			if (err != nil) != tt.wantErr {
				t.Errorf("validateExpiry() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestDetectCardBrand(t *testing.T) {
	tests := []struct {
		name string
		pan  string
		want string
	}{
		{"Visa", "4532015112830366", "VISA"},
		{"Mastercard 51", "5425233430109903", "MASTERCARD"},
		{"Mastercard 2221", "2221000000000009", "MASTERCARD"},
		{"Amex 34", "340000000000009", "AMEX"},
		{"Amex 37", "370000000000002", "AMEX"},
		{"Discover 6011", "6011000000000004", "DISCOVER"},
		{"Discover 65", "6500000000000002", "DISCOVER"},
		{"Unknown", "9000000000000000", "UNKNOWN"},
	}
	
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := detectCardBrand(tt.pan); got != tt.want {
				t.Errorf("detectCardBrand() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestTokenizeCard(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 24*time.Hour)
	
	pan := "4532015112830366"
	expiryMonth := 12
	expiryYear := 2025
	cvv := "123"
	
	tokenData, err := service.TokenizeCard(pan, expiryMonth, expiryYear, cvv)
	if err != nil {
		t.Fatalf("TokenizeCard() error = %v", err)
	}
	
	if tokenData.Token == "" {
		t.Error("Token should not be empty")
	}
	
	if tokenData.LastFour != "0366" {
		t.Errorf("LastFour = %v, want 0366", tokenData.LastFour)
	}
	
	if tokenData.CardBrand != "VISA" {
		t.Errorf("CardBrand = %v, want VISA", tokenData.CardBrand)
	}
	
	if !tokenData.IsActive {
		t.Error("Token should be active")
	}
}

func TestDetokenizeCard(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 24*time.Hour)
	
	pan := "4532015112830366"
	expiryMonth := 12
	expiryYear := 2025
	cvv := "123"
	
	// Tokenize
	tokenData, err := service.TokenizeCard(pan, expiryMonth, expiryYear, cvv)
	if err != nil {
		t.Fatalf("TokenizeCard() error = %v", err)
	}
	
	// Detokenize
	retrievedPAN, retrievedMonth, retrievedYear, err := service.DetokenizeCard(tokenData.Token)
	if err != nil {
		t.Fatalf("DetokenizeCard() error = %v", err)
	}
	
	if retrievedPAN != pan {
		t.Errorf("DetokenizeCard() PAN = %v, want %v", retrievedPAN, pan)
	}
	
	if retrievedMonth != expiryMonth {
		t.Errorf("DetokenizeCard() month = %v, want %v", retrievedMonth, expiryMonth)
	}
	
	if retrievedYear != expiryYear {
		t.Errorf("DetokenizeCard() year = %v, want %v", retrievedYear, expiryYear)
	}
}

func TestInvalidTokenRejection(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 24*time.Hour)
	
	tests := []struct {
		name  string
		token string
	}{
		{"Empty token", ""},
		{"Invalid format", "invalid"},
		{"Non-existent token", "9123456789012345"},
		{"Wrong prefix", "4123456789012345"},
	}
	
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, _, _, err := service.DetokenizeCard(tt.token)
			if err == nil {
				t.Error("DetokenizeCard() should return error for invalid token")
			}
		})
	}
}

func TestTokenUniqueness(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 24*time.Hour)
	
	pans := []string{
		"4532015112830366",
		"5425233430109903",
		"378282246310005",
		"6011000000000004",
	}
	
	tokens := make(map[string]bool)
	
	for _, pan := range pans {
		tokenData, err := service.TokenizeCard(pan, 12, 2025, "123")
		if err != nil {
			t.Fatalf("TokenizeCard() error = %v", err)
		}
		
		if tokens[tokenData.Token] {
			t.Errorf("Duplicate token generated: %v", tokenData.Token)
		}
		tokens[tokenData.Token] = true
	}
}

func TestExpiredToken(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 1*time.Nanosecond) // Very short TTL
	
	pan := "4532015112830366"
	tokenData, err := service.TokenizeCard(pan, 12, 2025, "123")
	if err != nil {
		t.Fatalf("TokenizeCard() error = %v", err)
	}
	
	// Wait for token to expire
	time.Sleep(10 * time.Millisecond)
	
	_, _, _, err = service.DetokenizeCard(tokenData.Token)
	if err != ErrTokenExpired {
		t.Errorf("DetokenizeCard() error = %v, want %v", err, ErrTokenExpired)
	}
}

func TestRevokeToken(t *testing.T) {
	mockHSM := &MockHSMClient{}
	service := NewService(mockHSM, "test-key", 24*time.Hour)
	
	pan := "4532015112830366"
	tokenData, err := service.TokenizeCard(pan, 12, 2025, "123")
	if err != nil {
		t.Fatalf("TokenizeCard() error = %v", err)
	}
	
	// Revoke token
	err = service.RevokeToken(tokenData.Token)
	if err != nil {
		t.Fatalf("RevokeToken() error = %v", err)
	}
	
	// Try to detokenize revoked token
	_, _, _, err = service.DetokenizeCard(tokenData.Token)
	if err != ErrTokenNotFound {
		t.Errorf("DetokenizeCard() error = %v, want %v", err, ErrTokenNotFound)
	}
}
