package com.paymentgateway.threeds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Simulates an Access Control Server (ACS) for testing purposes.
 * In production, this would be replaced with actual ACS integration.
 */
@Component
public class ACSSimulator {
    private static final Logger logger = LoggerFactory.getLogger(ACSSimulator.class);

    @Value("${threeds.acs.url:http://localhost:8448/acs}")
    private String acsUrl;

    public String getAcsUrl() {
        return acsUrl;
    }

    /**
     * Validates the PARes (Payment Authentication Response) from the ACS.
     * In production, this would involve cryptographic validation.
     */
    public boolean validatePARes(String pares) {
        if (pares == null || pares.isEmpty()) {
            logger.warn("Empty PARes received");
            return false;
        }

        try {
            // Decode and validate PARes
            byte[] decoded = Base64.getDecoder().decode(pares);
            String paresStr = new String(decoded);
            
            // Simple validation - in production, verify signature and parse XML
            boolean isValid = paresStr.contains("status=Y") || paresStr.contains("authenticated");
            
            logger.info("PARes validation result: {}", isValid);
            return isValid;
        } catch (Exception e) {
            logger.error("Failed to validate PARes", e);
            return false;
        }
    }

    /**
     * Simulates ACS authentication challenge.
     * Returns a mock PARes for testing.
     */
    public String simulateChallenge(String transactionId, boolean shouldSucceed) {
        String status = shouldSucceed ? "status=Y" : "status=N";
        String paresContent = String.format("transactionId=%s,%s,authenticated=%b", 
            transactionId, status, shouldSucceed);
        return Base64.getEncoder().encodeToString(paresContent.getBytes());
    }
}
