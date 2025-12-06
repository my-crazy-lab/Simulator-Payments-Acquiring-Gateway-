package com.paymentgateway.authorization.dto;

import com.paymentgateway.authorization.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

public class RefundResponse {
    
    private String refundId;
    private String paymentId;
    private RefundStatus status;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private Instant createdAt;
    private Instant processedAt;
    private String errorCode;
    private String errorMessage;
    
    // Constructors
    public RefundResponse() {}
    
    public RefundResponse(String refundId, String paymentId, RefundStatus status, 
                         BigDecimal amount, String currency) {
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
    }
    
    // Getters and Setters
    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    
    public RefundStatus getStatus() { return status; }
    public void setStatus(RefundStatus status) { this.status = status; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
