package com.paymentgateway.authorization.psp;

import java.math.BigDecimal;
import java.time.Instant;

public class PSPCaptureResponse {
    
    private boolean success;
    private String pspTransactionId;
    private BigDecimal capturedAmount;
    private String currency;
    private String errorCode;
    private String errorMessage;
    private Instant timestamp;
    
    public PSPCaptureResponse() {
        this.timestamp = Instant.now();
    }
    
    public PSPCaptureResponse(boolean success, String pspTransactionId) {
        this.success = success;
        this.pspTransactionId = pspTransactionId;
        this.timestamp = Instant.now();
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getPspTransactionId() { return pspTransactionId; }
    public void setPspTransactionId(String pspTransactionId) { this.pspTransactionId = pspTransactionId; }
    
    public BigDecimal getCapturedAmount() { return capturedAmount; }
    public void setCapturedAmount(BigDecimal capturedAmount) { this.capturedAmount = capturedAmount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
