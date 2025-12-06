package com.paymentgateway.authorization.psp;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface for Payment Service Provider (PSP) clients.
 * Implementations handle communication with external PSPs like Stripe, Adyen, etc.
 */
public interface PSPClient {
    
    /**
     * Get the name of this PSP
     */
    String getPSPName();
    
    /**
     * Authorize a payment transaction
     */
    PSPAuthorizationResponse authorize(PSPAuthorizationRequest request);
    
    /**
     * Capture a previously authorized payment
     */
    PSPCaptureResponse capture(String pspTransactionId, BigDecimal amount, String currency);
    
    /**
     * Void/cancel an authorized payment
     */
    PSPVoidResponse voidTransaction(String pspTransactionId);
    
    /**
     * Refund a captured payment
     */
    PSPRefundResponse refund(String pspTransactionId, BigDecimal amount, String currency);
    
    /**
     * Check if this PSP is currently available
     */
    boolean isAvailable();
}
