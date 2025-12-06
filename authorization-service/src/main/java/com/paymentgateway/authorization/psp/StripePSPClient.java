package com.paymentgateway.authorization.psp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stripe PSP client implementation.
 * In production, this would integrate with Stripe's actual API.
 * For now, this is a simulator that demonstrates the integration pattern.
 */
@Component
public class StripePSPClient implements PSPClient {
    
    private static final Logger logger = LoggerFactory.getLogger(StripePSPClient.class);
    private static final String PSP_NAME = "STRIPE";
    
    private boolean available = true;
    
    @Override
    public String getPSPName() {
        return PSP_NAME;
    }
    
    @Override
    public PSPAuthorizationResponse authorize(PSPAuthorizationRequest request) {
        logger.info("Stripe: Authorizing payment - merchantId={}, amount={}, currency={}",
                   request.getMerchantId(), request.getAmount(), request.getCurrency());
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Stripe is currently unavailable", true);
        }
        
        // Validate required fields
        validateAuthorizationRequest(request);
        
        // Simulate Stripe API call
        try {
            // In production, this would make an HTTP request to Stripe's API
            Thread.sleep(50); // Simulate network latency
            
            // Generate Stripe-style transaction ID
            String pspTransactionId = "ch_stripe_" + UUID.randomUUID().toString().substring(0, 20);
            
            // Simulate authorization success (90% success rate)
            if (Math.random() < 0.9) {
                logger.info("Stripe: Authorization successful - pspTransactionId={}", pspTransactionId);
                return PSPAuthorizationResponse.success(pspTransactionId, request.getAmount(), request.getCurrency());
            } else {
                logger.warn("Stripe: Authorization declined - insufficient_funds");
                return PSPAuthorizationResponse.declined("insufficient_funds", "Card has insufficient funds");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Stripe: Authorization failed", e);
            throw new PSPException(PSP_NAME, "Authorization request failed", e);
        }
    }
    
    @Override
    public PSPCaptureResponse capture(String pspTransactionId, BigDecimal amount, String currency) {
        logger.info("Stripe: Capturing payment - pspTransactionId={}, amount={}, currency={}",
                   pspTransactionId, amount, currency);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Stripe is currently unavailable", true);
        }
        
        try {
            Thread.sleep(30); // Simulate network latency
            
            PSPCaptureResponse response = new PSPCaptureResponse(true, pspTransactionId);
            response.setCapturedAmount(amount);
            response.setCurrency(currency);
            
            logger.info("Stripe: Capture successful - pspTransactionId={}", pspTransactionId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Stripe: Capture failed", e);
            throw new PSPException(PSP_NAME, "Capture request failed", e);
        }
    }
    
    @Override
    public PSPVoidResponse voidTransaction(String pspTransactionId) {
        logger.info("Stripe: Voiding payment - pspTransactionId={}", pspTransactionId);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Stripe is currently unavailable", true);
        }
        
        try {
            Thread.sleep(30); // Simulate network latency
            
            PSPVoidResponse response = new PSPVoidResponse(true, pspTransactionId);
            logger.info("Stripe: Void successful - pspTransactionId={}", pspTransactionId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Stripe: Void failed", e);
            throw new PSPException(PSP_NAME, "Void request failed", e);
        }
    }
    
    @Override
    public PSPRefundResponse refund(String pspTransactionId, BigDecimal amount, String currency) {
        logger.info("Stripe: Refunding payment - pspTransactionId={}, amount={}, currency={}",
                   pspTransactionId, amount, currency);
        
        if (!available) {
            throw new PSPException(PSP_NAME, "PSP_UNAVAILABLE", "Stripe is currently unavailable", true);
        }
        
        try {
            Thread.sleep(40); // Simulate network latency
            
            String refundId = "re_stripe_" + UUID.randomUUID().toString().substring(0, 20);
            PSPRefundResponse response = new PSPRefundResponse(true, refundId, pspTransactionId);
            response.setRefundedAmount(amount);
            response.setCurrency(currency);
            
            logger.info("Stripe: Refund successful - refundId={}", refundId);
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PSPException(PSP_NAME, "Request interrupted", e);
        } catch (Exception e) {
            logger.error("Stripe: Refund failed", e);
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
