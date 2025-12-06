package com.paymentgateway.authorization.psp;

import java.time.Instant;

public class PSPVoidResponse {
    
    private boolean success;
    private String pspTransactionId;
    private String errorCode;
    private String errorMessage;
    private Instant timestamp;
    
    public PSPVoidResponse() {
        this.timestamp = Instant.now();
    }
    
    public PSPVoidResponse(boolean success, String pspTransactionId) {
        this.success = success;
        this.pspTransactionId = pspTransactionId;
        this.timestamp = Instant.now();
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getPspTransactionId() { return pspTransactionId; }
    public void setPspTransactionId(String pspTransactionId) { this.pspTransactionId = pspTransactionId; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
