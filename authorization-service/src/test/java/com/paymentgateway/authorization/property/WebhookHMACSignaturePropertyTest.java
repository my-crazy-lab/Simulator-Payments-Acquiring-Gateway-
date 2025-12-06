package com.paymentgateway.authorization.property;

import com.paymentgateway.authorization.webhook.WebhookService;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: payment-acquiring-gateway, Property 32: Webhook HMAC Signature
 * 
 * For any webhook sent to a merchant, the payload should include a valid HMAC signature 
 * that can be verified using the merchant's webhook secret.
 * 
 * Validates: Requirements 22.2
 */
public class WebhookHMACSignaturePropertyTest {
    
    private final WebhookService webhookService = new WebhookService();
    
    /**
     * Property: For any payload and secret, generating a signature and then verifying it
     * with the same secret should always succeed.
     */
    @Property(tries = 100)
    void hmacSignatureRoundTrip(
            @ForAll("payloads") String payload,
            @ForAll("secrets") String secret) {
        
        // Generate HMAC signature
        String signature = webhookService.generateHmacSignature(payload, secret);
        
        // Verify the signature
        boolean isValid = webhookService.verifyHmacSignature(payload, signature, secret);
        
        // Assert that verification succeeds
        assertThat(isValid)
                .as("HMAC signature should be verifiable with the same secret")
                .isTrue();
    }
    
    /**
     * Property: For any payload, the same secret should always produce the same signature.
     * This tests determinism of the HMAC algorithm.
     */
    @Property(tries = 100)
    void hmacSignatureIsDeterministic(
            @ForAll("payloads") String payload,
            @ForAll("secrets") String secret) {
        
        // Generate signature twice
        String signature1 = webhookService.generateHmacSignature(payload, secret);
        String signature2 = webhookService.generateHmacSignature(payload, secret);
        
        // Assert signatures are identical
        assertThat(signature1)
                .as("HMAC signature should be deterministic")
                .isEqualTo(signature2);
    }
    
    /**
     * Property: For any payload, different secrets should produce different signatures.
     * This tests that the signature is actually dependent on the secret.
     */
    @Property(tries = 100)
    void differentSecretsProduceDifferentSignatures(
            @ForAll("payloads") String payload,
            @ForAll("secrets") String secret1,
            @ForAll("secrets") String secret2) {
        
        Assume.that(!secret1.equals(secret2));
        
        // Generate signatures with different secrets
        String signature1 = webhookService.generateHmacSignature(payload, secret1);
        String signature2 = webhookService.generateHmacSignature(payload, secret2);
        
        // Assert signatures are different
        assertThat(signature1)
                .as("Different secrets should produce different signatures")
                .isNotEqualTo(signature2);
    }
    
    /**
     * Property: For any payload, modifying the payload should invalidate the signature.
     * This tests that the signature is actually dependent on the payload content.
     */
    @Property(tries = 100)
    void modifiedPayloadInvalidatesSignature(
            @ForAll("payloads") String originalPayload,
            @ForAll("secrets") String secret) {
        
        Assume.that(originalPayload.length() > 0);
        
        // Generate signature for original payload
        String signature = webhookService.generateHmacSignature(originalPayload, secret);
        
        // Modify the payload slightly
        String modifiedPayload = originalPayload + "x";
        
        // Verify signature with modified payload
        boolean isValid = webhookService.verifyHmacSignature(modifiedPayload, signature, secret);
        
        // Assert that verification fails
        assertThat(isValid)
                .as("Modified payload should invalidate the signature")
                .isFalse();
    }
    
    /**
     * Property: For any payload and secret, using a different secret for verification
     * should fail.
     */
    @Property(tries = 100)
    void wrongSecretFailsVerification(
            @ForAll("payloads") String payload,
            @ForAll("secrets") String correctSecret,
            @ForAll("secrets") String wrongSecret) {
        
        Assume.that(!correctSecret.equals(wrongSecret));
        
        // Generate signature with correct secret
        String signature = webhookService.generateHmacSignature(payload, correctSecret);
        
        // Try to verify with wrong secret
        boolean isValid = webhookService.verifyHmacSignature(payload, signature, wrongSecret);
        
        // Assert that verification fails
        assertThat(isValid)
                .as("Wrong secret should fail signature verification")
                .isFalse();
    }
    
    /**
     * Property: For any payload, the signature should be a valid Base64 string.
     */
    @Property(tries = 100)
    void signatureIsValidBase64(
            @ForAll("payloads") String payload,
            @ForAll("secrets") String secret) {
        
        // Generate signature
        String signature = webhookService.generateHmacSignature(payload, secret);
        
        // Assert signature is not null or empty
        assertThat(signature)
                .as("Signature should not be null or empty")
                .isNotNull()
                .isNotEmpty();
        
        // Assert signature is valid Base64 (should not throw exception)
        assertThat(signature)
                .as("Signature should be valid Base64")
                .matches("^[A-Za-z0-9+/]*={0,2}$");
    }
    
    // Arbitraries (generators)
    
    @Provide
    Arbitrary<String> payloads() {
        return Arbitraries.oneOf(
                // JSON-like payloads
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .withChars('{', '}', '"', ':', ',', '[', ']', ' ')
                        .ofMinLength(10)
                        .ofMaxLength(1000),
                // Simple strings
                Arbitraries.strings()
                        .ascii()
                        .ofMinLength(1)
                        .ofMaxLength(500),
                // Empty payload edge case
                Arbitraries.just("")
        );
    }
    
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(16)
                .ofMaxLength(64);
    }
}
