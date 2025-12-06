package com.paymentgateway.authorization.audit;

import com.paymentgateway.authorization.domain.PaymentEvent;
import com.paymentgateway.authorization.repository.PaymentEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for creating and managing immutable audit logs with cryptographic integrity.
 * Implements PCI DSS compliant audit logging with PAN/CVV redaction.
 */
@Service
public class AuditLogService {
    
    private final PaymentEventRepository paymentEventRepository;
    private final String hmacSecretKey;
    
    // Pattern to detect PAN (13-19 digits)
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b\\d{13,19}\\b");
    
    // Pattern to detect CVV (3-4 digits)
    private static final Pattern CVV_PATTERN = Pattern.compile("\\b(cvv|cvc|security_code)[\"']?\\s*[:=]\\s*[\"']?\\d{3,4}\\b", Pattern.CASE_INSENSITIVE);
    
    public AuditLogService(PaymentEventRepository paymentEventRepository) {
        this.paymentEventRepository = paymentEventRepository;
        // In production, this should come from secure configuration/vault
        this.hmacSecretKey = System.getenv().getOrDefault("AUDIT_HMAC_SECRET", "default-secret-key-change-in-production");
    }
    
    /**
     * Create an immutable audit log entry with cryptographic integrity verification.
     * All sensitive data (PAN, CVV) is automatically redacted.
     * 
     * @param paymentId The payment ID this event relates to
     * @param eventType The type of event (AUTHORIZATION, CAPTURE, etc.)
     * @param eventStatus The status of the event (SUCCESS, FAILED, etc.)
     * @param description Human-readable description of the event
     * @param actor The actor performing the operation (merchant ID, system, etc.)
     * @return The created audit log entry
     */
    @Transactional
    public PaymentEvent createAuditLog(UUID paymentId, String eventType, String eventStatus, 
                                       String description, String actor) {
        PaymentEvent event = new PaymentEvent();
        event.setPaymentId(paymentId);
        event.setEventType(eventType);
        event.setEventStatus(eventStatus);
        
        // Redact sensitive data from description
        String redactedDescription = redactSensitiveData(description);
        event.setDescription(redactedDescription);
        
        // Add actor information to gateway response
        String actorInfo = String.format("{\"actor\": \"%s\", \"timestamp\": \"%s\"}", 
                                        actor, Instant.now().toString());
        event.setGatewayResponse(actorInfo);
        
        event.setCreatedAt(Instant.now());
        event.setCorrelationId(UUID.randomUUID());
        
        // Save the event (immutable - no updates allowed)
        PaymentEvent savedEvent = paymentEventRepository.save(event);
        
        // Generate and store HMAC signature for integrity verification
        String signature = generateHmacSignature(savedEvent);
        savedEvent.setErrorCode("HMAC:" + signature); // Store signature in error_code field
        
        return paymentEventRepository.save(savedEvent);
    }
    
    /**
     * Create an audit log with additional metadata.
     */
    @Transactional
    public PaymentEvent createDetailedAuditLog(AuditLogEntry entry) {
        PaymentEvent event = new PaymentEvent();
        event.setPaymentId(entry.getPaymentId());
        event.setEventType(entry.getEventType());
        event.setEventStatus(entry.getEventStatus());
        event.setAmount(entry.getAmount());
        event.setCurrency(entry.getCurrency());
        
        // Redact sensitive data
        event.setDescription(redactSensitiveData(entry.getDescription()));
        event.setPspResponse(redactSensitiveData(entry.getPspResponse()));
        event.setGatewayResponse(redactSensitiveData(entry.getGatewayResponse()));
        
        event.setProcessingTimeMs(entry.getProcessingTimeMs());
        event.setErrorMessage(redactSensitiveData(entry.getErrorMessage()));
        event.setUserAgent(entry.getUserAgent());
        event.setIpAddress(entry.getIpAddress());
        event.setCorrelationId(entry.getCorrelationId() != null ? entry.getCorrelationId() : UUID.randomUUID());
        event.setCreatedAt(Instant.now());
        
        // Save the event
        PaymentEvent savedEvent = paymentEventRepository.save(event);
        
        // Generate and store HMAC signature
        String signature = generateHmacSignature(savedEvent);
        savedEvent.setErrorCode("HMAC:" + signature);
        
        return paymentEventRepository.save(savedEvent);
    }
    
    /**
     * Retrieve audit logs for a payment with integrity verification.
     * This method is read-only and enforces immutability.
     * 
     * @param paymentId The payment ID to retrieve logs for
     * @return List of audit log entries
     */
    @Transactional(readOnly = true)
    public List<PaymentEvent> getAuditLogs(UUID paymentId) {
        return paymentEventRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }
    
    /**
     * Verify the cryptographic integrity of an audit log entry.
     * 
     * @param event The audit log entry to verify
     * @return true if the integrity check passes, false otherwise
     */
    public boolean verifyIntegrity(PaymentEvent event) {
        if (event.getErrorCode() == null || !event.getErrorCode().startsWith("HMAC:")) {
            return false;
        }
        
        String storedSignature = event.getErrorCode().substring(5);
        
        // Temporarily clear the signature field to recalculate
        String originalErrorCode = event.getErrorCode();
        event.setErrorCode(null);
        
        String calculatedSignature = generateHmacSignature(event);
        
        // Restore the original signature
        event.setErrorCode(originalErrorCode);
        
        return storedSignature.equals(calculatedSignature);
    }
    
    /**
     * Redact sensitive data (PAN, CVV) from text.
     * 
     * @param text The text to redact
     * @return The redacted text
     */
    private String redactSensitiveData(String text) {
        if (text == null) {
            return null;
        }
        
        // Redact PAN (replace with masked version showing only last 4 digits)
        String redacted = PAN_PATTERN.matcher(text).replaceAll(matchResult -> {
            String pan = matchResult.group();
            if (pan.length() >= 4) {
                return "****" + pan.substring(pan.length() - 4);
            }
            return "****";
        });
        
        // Redact CVV completely
        redacted = CVV_PATTERN.matcher(redacted).replaceAll(matchResult -> {
            String match = matchResult.group();
            return match.replaceAll("\\d{3,4}", "***");
        });
        
        return redacted;
    }
    
    /**
     * Generate HMAC-SHA256 signature for audit log integrity verification.
     * 
     * @param event The audit log entry
     * @return Base64-encoded HMAC signature
     */
    private String generateHmacSignature(PaymentEvent event) {
        try {
            // Create canonical representation of the event
            String canonical = String.format("%s|%s|%s|%s|%s|%s",
                event.getId(),
                event.getPaymentId(),
                event.getEventType(),
                event.getEventStatus(),
                event.getCreatedAt(),
                event.getDescription() != null ? event.getDescription() : ""
            );
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecretKey.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate HMAC signature", e);
        }
    }
}
