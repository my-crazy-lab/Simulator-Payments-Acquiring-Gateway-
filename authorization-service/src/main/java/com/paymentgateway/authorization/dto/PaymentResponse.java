package com.paymentgateway.authorization.dto;

import com.paymentgateway.authorization.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public class PaymentResponse {
    
    private String paymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String cardLastFour;
    private String cardBrand;
    private Instant createdAt;
    private Instant authorizedAt;
    private String errorCode;
    private String errorMessage;
    
    // Constructors
    public PaymentResponse() {}
    
    public PaymentResponse(String paymentId, PaymentStatus status, BigDecimal amount, String currency) {
        this.paymentId = paymentId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
    }
    
    // Getters and Setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
    
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(Instant authorizedAt) { this.authorizedAt = authorizedAt; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
