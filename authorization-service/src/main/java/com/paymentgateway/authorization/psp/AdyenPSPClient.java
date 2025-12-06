package com.paymentgateway.authorization.psp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adyen PSP client implementation.
 * In production, this would integrate with Adyen's actual API.
 * For now, this is a simulator that demonstrates the integration pattern.
 */
@Component
public class AdyenPSPClient implements PSPClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AdyenPSPClient.class);
    private static final String PSP_NAME = "ADYEN";
    
    private boolean available = true;
    
    @Override
    public String getPSPName() {
        return PSP_NAME;
    }
    
    @Override
    public PSPAuthorizationResponse authorize(PSPAuthorizationRequest request) {
        logger.info("Adyen: Authorizing payment - merchantId={}, amount={}, currency={}",
                   request.getMerchantId(), request.getAmount(), request.getCurrency());
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Adyen is currently unavailable", true);
        }
        
        // Validate required fields
        validateAuthorizationRequest(request);
        
        // Simulate Adyen API call
        try {
            // In production, this would make an HTTP request to Adyen's API
            Thread.sleep(60); // Simulate slightly higher network latency
            
            // Generate Adyen-style transaction ID
            String pspTransactionId = "adyen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            
            // Simulate authorization success (92% success rate - slightly better than Stripe)
            if (Math.random() < 0.92) {
                logger.info("Adyen: Authorization successful - pspTransactionId={}", pspTransactionId);
                return PSPAuthorizationResponse.success(pspTransactionId, request.getAmount(), request.getCurrency());
            } else {
                logger.warn("Adyen: Authorization declined - card_declined");
                return PSPAuthorizationResponse.declined("card_declined", "Card was declined by issuer");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Adyen: Authorization failed", e);
            throw new PSPException(PSP_NAME, "Authorization request failed", e);
        }
    }
    
    @Override
    public PSPCaptureResponse capture(String pspTransactionId, BigDecimal amount, String currency) {
        logger.info("Adyen: Capturing payment - pspTransactionId={}, amount={}, currency={}",
                   pspTransactionId, amount, currency);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Adyen is currently unavailable", true);
        }
        
        try {
            Thread.sleep(35); // Simulate network latency
            
            PSPCaptureResponse response = new PSPCaptureResponse(true, pspTransactionId);
            response.setCapturedAmount(amount);
            response.setCurrency(currency);
            
            logger.info("Adyen: Capture successful - pspTransactionId={}", pspTransactionId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Adyen: Capture failed", e);
            throw new PSPException(PSP_NAME, "Capture request failed", e);
        }
    }
    
    @Override
    public PSPVoidResponse voidTransaction(String pspTransactionId) {
        logger.info("Adyen: Voiding payment - pspTransactionId={}", pspTransactionId);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Adyen is currently unavailable", true);
        }
        
        try {
            Thread.sleep(35); // Simulate network latency
            
            PSPVoidResponse response = new PSPVoidResponse(true, pspTransactionId);
            logger.info("Adyen: Void successful - pspTransactionId={}", pspTransactionId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Adyen: Void failed", e);
            throw new PSPException(PSP_NAME, "Void request failed", e);
        }
    }
    
    @Override
    public PSPRefundResponse refund(String pspTransactionId, BigDecimal amount, String currency) {
        logger.info("Adyen: Refunding payment - pspTransactionId={}, amount={}, currency={}",
                   pspTransactionId, amount, currency);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Adyen is currently unavailable", true);
        }
        
        try {
            Thread.sleep(45); // Simulate network latency
            
            String refundId = "adyen_ref_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            PSPRefundResponse response = new PSPRefundResponse(true, refundId, pspTransactionId);
            response.setRefundedAmount(amount);
            response.setCurrency(currency);
            
            logger.info("Adyen: Refund successful - refundId={}", refundId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Adyen: Refund failed", e);
            throw new PSPException(PSP_NAME, "Refund request failed", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    // For testing purposes
    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    private void validateAuthorizationRequest(PSPAuthorizationRequest request) {
        if (request.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getCardTokenId() == null) {
            throw new IllegalArgumentException("Card token ID is required");
        }
    }
}
