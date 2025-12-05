package hsm

import (
	"testing"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

/**
 * Feature: payment-acquiring-gateway, Property 22: Key Rotation Backward Compatibility
 * For any data encrypted with an old key version, after key rotation, the data should
 * still be decryptable using the old key version identifier.
 * Validates: Requirements 11.4
 */
func TestProperty_KeyRotationBackwardCompatibility(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("data encrypted with old key version remains decryptable after rotation", prop.ForAll(
		func(keyID string, plaintext []byte, aad []byte) bool {
			hsm := NewHSM()
			
			// Generate initial key
			_, err := hsm.GenerateKey(keyID, "AES-256-GCM")
			if err != nil {
				return false
			}
			
			// Encrypt with version 1
			ciphertext, nonce, keyVersion, err := hsm.Encrypt(keyID, plaintext, aad)
			if err != nil {
				return false
			}
			
			// Verify we're using version 1
			if keyVersion != 1 {
				return false
			}
			
			// Rotate the key
			newVersion, oldVersion, err := hsm.RotateKey(keyID)
			if err != nil {
				return false
			}
			
			// Verify rotation created a new version
			if newVersion != 2 || oldVersion != 1 {
				return false
			}
			
			// Decrypt using the OLD key version (backward compatibility)
			decrypted, err := hsm.Decrypt(keyID, ciphertext, nonce, aad, keyVersion)
			if err != nil {
				return false
			}
			
			// Verify the data is still correct
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
 * Feature: payment-acquiring-gateway, Property 22: Key Rotation Backward Compatibility (Multiple Rotations)
 * For any data encrypted with any old key version, after multiple rotations, the data should
 * still be decryptable using the original key version identifier.
 * Validates: Requirements 11.4
 */
func TestProperty_MultipleKeyRotationsBackwardCompatibility(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("data remains decryptable after multiple key rotations", prop.ForAll(
		func(keyID string, plaintext []byte, aad []byte, rotationCount uint8) bool {
			// Limit rotations to reasonable number
			numRotations := int(rotationCount%5) + 1
			
			hsm := NewHSM()
			
			// Generate initial key
			_, err := hsm.GenerateKey(keyID, "AES-256-GCM")
			if err != nil {
				return false
			}
			
			// Encrypt with version 1
			ciphertext, nonce, keyVersion, err := hsm.Encrypt(keyID, plaintext, aad)
			if err != nil {
				return false
			}
			
			// Perform multiple rotations
			for i := 0; i < numRotations; i++ {
				_, _, err := hsm.RotateKey(keyID)
				if err != nil {
					return false
				}
			}
			
			// Verify key info shows all versions are available
			keyInfo, err := hsm.GetKeyInfo(keyID)
			if err != nil {
				return false
			}
			
			// Should have original version + rotations
			expectedVersions := 1 + numRotations
			if len(keyInfo.AvailableVersions) != expectedVersions {
				return false
			}
			
			// Decrypt using the ORIGINAL key version (version 1)
			decrypted, err := hsm.Decrypt(keyID, ciphertext, nonce, aad, keyVersion)
			if err != nil {
				return false
			}
			
			// Verify the data is still correct
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
		gen.UInt8(),
	))

	properties.TestingRun(t)
}

/**
 * Feature: payment-acquiring-gateway, Property 22: Key Rotation Backward Compatibility (New Encryptions Use New Key)
 * For any data encrypted after key rotation, it should use the new key version,
 * while old data remains decryptable with old versions.
 * Validates: Requirements 11.4
 */
func TestProperty_NewEncryptionsUseNewKeyAfterRotation(t *testing.T) {
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)

	properties.Property("new encryptions use new key version after rotation", prop.ForAll(
		func(keyID string, plaintext1 []byte, plaintext2 []byte, aad []byte) bool {
			hsm := NewHSM()
			
			// Generate initial key
			_, err := hsm.GenerateKey(keyID, "AES-256-GCM")
			if err != nil {
				return false
			}
			
			// Encrypt with version 1
			ciphertext1, nonce1, keyVersion1, err := hsm.Encrypt(keyID, plaintext1, aad)
			if err != nil {
				return false
			}
			
			if keyVersion1 != 1 {
				return false
			}
			
			// Rotate the key
			newVersion, _, err := hsm.RotateKey(keyID)
			if err != nil {
				return false
			}
			
			// Encrypt new data - should use new version
			ciphertext2, nonce2, keyVersion2, err := hsm.Encrypt(keyID, plaintext2, aad)
			if err != nil {
				return false
			}
			
			// New encryption should use new version
			if keyVersion2 != newVersion {
				return false
			}
			
			// Both should be decryptable with their respective versions
			decrypted1, err := hsm.Decrypt(keyID, ciphertext1, nonce1, aad, keyVersion1)
			if err != nil {
				return false
			}
			
			decrypted2, err := hsm.Decrypt(keyID, ciphertext2, nonce2, aad, keyVersion2)
			if err != nil {
				return false
			}
			
			// Verify both decryptions are correct
			if len(plaintext1) != len(decrypted1) {
				return false
			}
			for i := range plaintext1 {
				if plaintext1[i] != decrypted1[i] {
					return false
				}
			}
			
			if len(plaintext2) != len(decrypted2) {
				return false
			}
			for i := range plaintext2 {
				if plaintext2[i] != decrypted2[i] {
					return false
				}
			}
			
			return true
		},
		gen.Identifier().SuchThat(func(s string) bool { return len(s) > 0 && len(s) < 100 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) > 0 && len(b) < 1000 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) > 0 && len(b) < 1000 }),
		gen.SliceOf(gen.UInt8()).SuchThat(func(b []byte) bool { return len(b) < 100 }),
	))

	properties.TestingRun(t)
}
