package com.paymentgateway.authorization.security;

import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyGenerator {
    
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    
    /**
     * Generate a cryptographically secure API key
     */
    public static String generate() {
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        return "sk_" + base64Encoder.encodeToString(randomBytes);
    }
}
