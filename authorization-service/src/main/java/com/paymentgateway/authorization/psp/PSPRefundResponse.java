package com.paymentgateway.authorization.psp;

import java.math.BigDecimal;
import java.time.Instant;

public class PSPRefundResponse {
    
    private boolean success;
    private String pspRefundId;
    private String pspTransactionId;
    private BigDecimal refundedAmount;
    private String currency;
    private String errorCode;
    private String errorMessage;
    private Instant timestamp;
    
    public PSPRefundResponse() {
        this.timestamp = Instant.now();
    }
    
    public PSPRefundResponse(boolean success, String pspRefundId, String pspTransactionId) {
        this.success = success;
        this.pspRefundId = pspRefundId;
        this.pspTransactionId = pspTransactionId;
        this.timestamp = Instant.now();
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getPspRefundId() { return pspRefundId; }
    public void setPspRefundId(String pspRefundId) { this.pspRefundId = pspRefundId; }
    
    public String getPspTransactionId() { return pspTransactionId; }
    public void setPspTransactionId(String pspTransactionId) { this.pspTransactionId = pspTransactionId; }
    
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
