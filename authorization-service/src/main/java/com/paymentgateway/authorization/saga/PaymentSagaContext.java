package com.paymentgateway.authorization.saga;

import com.paymentgateway.authorization.domain.Payment;
import com.paymentgateway.authorization.domain.PaymentStatus;
import com.paymentgateway.authorization.dto.PaymentRequest;

import java.util.UUID;

/**
 * Context object shared across all steps in a payment saga.
 * Contains the payment request, intermediate results, and final payment entity.
 */
public class PaymentSagaContext {
    
    // Input
    private final PaymentRequest request;
    private final UUID merchantId;
    private final String paymentId;
    
    // Intermediate results
    private UUID cardTokenId;
    private String cardLastFour;
    private Double fraudScore;
    private boolean fraudCheckPassed;
    private boolean threeDsRequired;
    private boolean threeDsAuthenticated;
    private String threeDsCavv;
    private String threeDsEci;
    private String pspTransactionId;
    private boolean pspAuthorized;
    
    // Final result
    private Payment payment;
    private PaymentStatus finalStatus;
    private String failureReason;
    
    // Compensation tracking
    private boolean tokenCreated;
    private boolean fraudAlertCreated;
    private boolean threeDsSessionCreated;
    private boolean pspAuthorizationCreated;
    private boolean paymentRecordCreated;
    
    public PaymentSagaContext(PaymentRequest request, UUID merchantId, String paymentId) {
        this.request = request;
        this.merchantId = merchantId;
        this.paymentId = paymentId;
    }
    
    // Getters and setters
    public PaymentRequest getRequest() {
        return request;
    }
    
    public UUID getMerchantId() {
        return merchantId;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public UUID getCardTokenId() {
        return cardTokenId;
    }
    
    public void setCardTokenId(UUID cardTokenId) {
        this.cardTokenId = cardTokenId;
    }
    
    public String getCardLastFour() {
        return cardLastFour;
    }
    
    public void setCardLastFour(String cardLastFour) {
        this.cardLastFour = cardLastFour;
    }
    
    public Double getFraudScore() {
        return fraudScore;
    }
    
    public void setFraudScore(Double fraudScore) {
        this.fraudScore = fraudScore;
    }
    
    public boolean isFraudCheckPassed() {
        return fraudCheckPassed;
    }
    
    public void setFraudCheckPassed(boolean fraudCheckPassed) {
        this.fraudCheckPassed = fraudCheckPassed;
    }
    
    public boolean isThreeDsRequired() {
        return threeDsRequired;
    }
    
    public void setThreeDsRequired(boolean threeDsRequired) {
        this.threeDsRequired = threeDsRequired;
    }
    
    public boolean isThreeDsAuthenticated() {
        return threeDsAuthenticated;
    }
    
    public void setThreeDsAuthenticated(boolean threeDsAuthenticated) {
        this.threeDsAuthenticated = threeDsAuthenticated;
    }
    
    public String getThreeDsCavv() {
        return threeDsCavv;
    }
    
    public void setThreeDsCavv(String threeDsCavv) {
        this.threeDsCavv = threeDsCavv;
    }
    
    public String getThreeDsEci() {
        return threeDsEci;
    }
    
    public void setThreeDsEci(String threeDsEci) {
        this.threeDsEci = threeDsEci;
    }
    
    public String getPspTransactionId() {
        return pspTransactionId;
    }
    
    public void setPspTransactionId(String pspTransactionId) {
        this.pspTransactionId = pspTransactionId;
    }
    
    public boolean isPspAuthorized() {
        return pspAuthorized;
    }
    
    public void setPspAuthorized(boolean pspAuthorized) {
        this.pspAuthorized = pspAuthorized;
    }
    
    public Payment getPayment() {
        return payment;
    }
    
    public void setPayment(Payment payment) {
        this.payment = payment;
    }
    
    public PaymentStatus getFinalStatus() {
        return finalStatus;
    }
    
    public void setFinalStatus(PaymentStatus finalStatus) {
        this.finalStatus = finalStatus;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
    
    // Compensation tracking
    public boolean isTokenCreated() {
        return tokenCreated;
    }
    
    public void setTokenCreated(boolean tokenCreated) {
        this.tokenCreated = tokenCreated;
    }
    
    public boolean isFraudAlertCreated() {
        return fraudAlertCreated;
    }
    
    public void setFraudAlertCreated(boolean fraudAlertCreated) {
        this.fraudAlertCreated = fraudAlertCreated;
    }
    
    public boolean isThreeDsSessionCreated() {
        return threeDsSessionCreated;
    }
    
    public void setThreeDsSessionCreated(boolean threeDsSessionCreated) {
        this.threeDsSessionCreated = threeDsSessionCreated;
    }
    
    public boolean isPspAuthorizationCreated() {
        return pspAuthorizationCreated;
    }
    
    public void setPspAuthorizationCreated(boolean pspAuthorizationCreated) {
        this.pspAuthorizationCreated = pspAuthorizationCreated;
    }
    
    public boolean isPaymentRecordCreated() {
        return paymentRecordCreated;
    }
    
    public void setPaymentRecordCreated(boolean paymentRecordCreated) {
        this.paymentRecordCreated = paymentRecordCreated;
    }
}
