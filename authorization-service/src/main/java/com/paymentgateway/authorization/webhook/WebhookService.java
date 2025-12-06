package com.paymentgateway.authorization.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.authorization.domain.Merchant;
import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.WebhookDelivery;
import com.paymentgateway.authorization.event.PaymentEventMessage;
import com.paymentgateway.authorization.repository.MerchantRepository;
import com.paymentgateway.authorization.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int INITIAL_RETRY_DELAY_SECONDS = 60; // 1 minute
    private static final int MAX_RETRY_DELAY_SECONDS = 3600; // 1 hour
    
    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;
    
    @Autowired
    private MerchantRepository merchantRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Send webhook notification for a payment event.
     * This method is called asynchronously when payment events occur.
     */
    @Async
    public void sendWebhook(Payment payment, PaymentEventMessage eventMessage) {
        try {
            Merchant merchant = merchantRepository.findById(payment.getMerchantId())
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
            
            // Check if merchant has webhook configured
            if (merchant.getWebhookUrl() == null || merchant.getWebhookUrl().isEmpty()) {
                logger.debug("No webhook URL configured for merchant {}", merchant.getMerchantId());
                return;
            }
            
            if (merchant.getWebhookSecretHash() == null || merchant.getWebhookSecretHash().isEmpty()) {
                logger.warn("No webhook secret configured for merchant {}", merchant.getMerchantId());
                return;
            }
            
            // Serialize payload
            String payload = objectMapper.writeValueAsString(eventMessage);
            
            // Generate HMAC signature
            String signature = generateHmacSignature(payload, merchant.getWebhookSecretHash());
            
            // Create webhook delivery record
            WebhookDelivery delivery = new WebhookDelivery(
                    merchant.getId(),
                    payment.getId(),
                    eventMessage.getEventType().name(),
                    merchant.getWebhookUrl(),
                    payload,
                    signature
            );
            
            delivery.setNextRetryAt(Instant.now());
            webhookDeliveryRepository.save(delivery);
            
            // Attempt immediate delivery
            attemptDelivery(delivery);
            
        } catch (Exception e) {
            logger.error("Error preparing webhook for payment {}", payment.getId(), e);
        }
    }
    
    /**
     * Generate HMAC-SHA256 signature for webhook payload.
     * 
     * @param payload The webhook payload as JSON string
     * @param secret The merchant's webhook secret
     * @return Base64-encoded HMAC signature
     */
    public String generateHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Error generating HMAC signature", e);
            throw new RuntimeException("Failed to generate webhook signature", e);
        }
    }
    
    /**
     * Verify HMAC signature for incoming webhook verification requests.
     * 
     * @param payload The webhook payload
     * @param providedSignature The signature to verify
     * @param secret The merchant's webhook secret
     * @return true if signature is valid
     */
    public boolean verifyHmacSignature(String payload, String providedSignature, String secret) {
        String expectedSignature = generateHmacSignature(payload, secret);
        return expectedSignature.equals(providedSignature);
    }
    
    /**
     * Attempt to deliver a webhook.
     * Updates delivery record with result.
     */
    private void attemptDelivery(WebhookDelivery delivery) {
        try {
            delivery.incrementAttemptCount();
            
            // Prepare HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-Webhook-Signature", delivery.getSignature());
            headers.set("X-Webhook-Event-Type", delivery.getEventType());
            headers.set("X-Webhook-Delivery-Id", delivery.getId().toString());
            headers.set("X-Webhook-Attempt", String.valueOf(delivery.getAttemptCount()));
            
            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);
            
            // Send webhook
            ResponseEntity<String> response = restTemplate.exchange(
                    delivery.getWebhookUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );
            
            // Update delivery record with success
            delivery.setHttpStatusCode(response.getStatusCode().value());
            delivery.setResponseBody(response.getBody());
            
            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus("DELIVERED");
                delivery.setDeliveredAt(Instant.now());
                logger.info("Webhook delivered successfully for payment {} (attempt {})",
                        delivery.getPaymentId(), delivery.getAttemptCount());
            } else {
                handleDeliveryFailure(delivery, "Non-2xx status code: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            handleDeliveryFailure(delivery, e.getMessage());
        } finally {
            webhookDeliveryRepository.save(delivery);
        }
    }
    
    /**
     * Handle webhook delivery failure.
     * Implements exponential backoff retry logic.
     */
    private void handleDeliveryFailure(WebhookDelivery delivery, String errorMessage) {
        delivery.setErrorMessage(errorMessage);
        
        if (delivery.hasReachedMaxAttempts()) {
            delivery.setStatus("FAILED");
            logger.error("Webhook delivery failed after {} attempts for payment {}",
                    delivery.getAttemptCount(), delivery.getPaymentId());
        } else {
            // Calculate next retry time with exponential backoff
            int delaySeconds = calculateBackoffDelay(delivery.getAttemptCount());
            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
            delivery.setStatus("PENDING");
            
            logger.warn("Webhook delivery failed for payment {} (attempt {}), will retry in {} seconds",
                    delivery.getPaymentId(), delivery.getAttemptCount(), delaySeconds);
        }
    }
    
    /**
     * Calculate exponential backoff delay.
     * Formula: min(INITIAL_DELAY * 2^(attempt-1), MAX_DELAY)
     */
    private int calculateBackoffDelay(int attemptCount) {
        int delay = INITIAL_RETRY_DELAY_SECONDS * (int) Math.pow(2, attemptCount - 1);
        return Math.min(delay, MAX_RETRY_DELAY_SECONDS);
    }
    
    /**
     * Scheduled job to process pending webhook retries.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    public void processRetries() {
        List<WebhookDelivery> pendingRetries = webhookDeliveryRepository.findPendingRetries(Instant.now());
        
        logger.debug("Processing {} pending webhook retries", pendingRetries.size());
        
        for (WebhookDelivery delivery : pendingRetries) {
            attemptDelivery(delivery);
        }
    }
    
    /**
     * Get webhook delivery history for a merchant.
     */
    public List<WebhookDelivery> getDeliveryHistory(UUID merchantId) {
        return webhookDeliveryRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }
    
    /**
     * Get webhook delivery history for a specific payment.
     */
    public List<WebhookDelivery> getPaymentDeliveryHistory(UUID paymentId) {
        return webhookDeliveryRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }
}
