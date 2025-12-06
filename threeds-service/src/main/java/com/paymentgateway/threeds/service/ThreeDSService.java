package com.paymentgateway.threeds.service;

import com.paymentgateway.threeds.domain.BrowserInfo;
import com.paymentgateway.threeds.domain.ThreeDSStatus;
import com.paymentgateway.threeds.domain.ThreeDSTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class ThreeDSService {
    private static final Logger logger = LoggerFactory.getLogger(ThreeDSService.class);
    private static final String REDIS_KEY_PREFIX = "3ds:transaction:";
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(10);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final RedisTemplate<String, ThreeDSTransaction> redisTemplate;
    private final ACSSimulator acsSimulator;

    public ThreeDSService(RedisTemplate<String, ThreeDSTransaction> redisTemplate,
                         ACSSimulator acsSimulator) {
        this.redisTemplate = redisTemplate;
        this.acsSimulator = acsSimulator;
    }

    public ThreeDSTransaction initiateAuthentication(String transactionId, String merchantId,
                                                     BigDecimal amount, String currency,
                                                     String cardToken, String merchantReturnUrl,
                                                     BrowserInfo browserInfo) {
        logger.info("Initiating 3DS authentication for transaction: {}", transactionId);

        ThreeDSTransaction transaction = new ThreeDSTransaction(
            transactionId, merchantId, amount, currency, cardToken, 
            merchantReturnUrl, browserInfo
        );

        // Perform risk-based authentication decision
        boolean requiresChallenge = shouldRequireChallenge(transaction);

        if (requiresChallenge) {
            // Challenge flow - redirect to ACS
            transaction.setStatus(ThreeDSStatus.CHALLENGE_REQUIRED);
            transaction.setAcsUrl(acsSimulator.getAcsUrl());
            transaction.setXid(generateXid());
            logger.info("Challenge required for transaction: {}", transactionId);
        } else {
            // Frictionless flow - authenticate without challenge
            transaction.setStatus(ThreeDSStatus.FRICTIONLESS);
            transaction.setCavv(generateCavv());
            transaction.setEci(determineEci(true));
            transaction.setXid(generateXid());
            logger.info("Frictionless authentication for transaction: {}", transactionId);
        }

        // Store transaction state in Redis
        storeTransaction(transaction);

        return transaction;
    }

    public ThreeDSTransaction completeAuthentication(String transactionId, String pares) {
        logger.info("Completing 3DS authentication for transaction: {}", transactionId);

        ThreeDSTransaction transaction = getTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        if (transaction.isExpired()) {
            transaction.setStatus(ThreeDSStatus.TIMEOUT);
            transaction.setErrorMessage("Authentication timeout");
            storeTransaction(transaction);
            logger.warn("Transaction expired: {}", transactionId);
            return transaction;
        }

        // Validate PARes from ACS
        boolean authSuccess = acsSimulator.validatePARes(pares);

        if (authSuccess) {
            transaction.setStatus(ThreeDSStatus.AUTHENTICATED);
            transaction.setCavv(generateCavv());
            transaction.setEci(determineEci(true));
            logger.info("Authentication successful for transaction: {}", transactionId);
        } else {
            transaction.setStatus(ThreeDSStatus.FAILED);
            transaction.setErrorMessage("Authentication failed");
            logger.warn("Authentication failed for transaction: {}", transactionId);
        }

        storeTransaction(transaction);
        return transaction;
    }

    public ThreeDSTransaction getTransaction(String transactionId) {
        String key = REDIS_KEY_PREFIX + transactionId;
        return redisTemplate.opsForValue().get(key);
    }

    private void storeTransaction(ThreeDSTransaction transaction) {
        String key = REDIS_KEY_PREFIX + transaction.getTransactionId();
        redisTemplate.opsForValue().set(key, transaction, TRANSACTION_TTL);
    }

    private boolean shouldRequireChallenge(ThreeDSTransaction transaction) {
        // Risk-based authentication logic
        // For simplicity, require challenge for amounts > 100
        return transaction.getAmount().compareTo(new BigDecimal("100.00")) > 0;
    }

    /**
     * Generate CAVV (Cardholder Authentication Verification Value)
     * In production, this would be generated by the ACS
     */
    private String generateCavv() {
        byte[] cavvBytes = new byte[20];
        secureRandom.nextBytes(cavvBytes);
        return Base64.getEncoder().encodeToString(cavvBytes);
    }

    /**
     * Determine ECI (Electronic Commerce Indicator)
     * 05 = Fully authenticated
     * 06 = Attempted authentication
     * 07 = No authentication
     */
    private String determineEci(boolean authenticated) {
        return authenticated ? "05" : "07";
    }

    /**
     * Generate XID (Transaction Identifier)
     */
    private String generateXid() {
        try {
            String uuid = UUID.randomUUID().toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uuid.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 28);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate XID", e);
        }
    }
}
