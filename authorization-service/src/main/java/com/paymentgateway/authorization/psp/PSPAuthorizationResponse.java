package com.paymentgateway.authorization.psp;

import java.math.BigDecimal;
import java.time.Instant;

public class PSPAuthorizationResponse {
    
    private boolean success;
    private String pspTransactionId;
    private String status; // AUTHORIZED, DECLINED, ERROR
    private BigDecimal authorizedAmount;
    private String currency;
    private String declineCode;
    private String declineMessage;
    private String errorCode;
    private String errorMessage;
    private Instant timestamp;
    
    // Constructors
    public PSPAuthorizationResponse() {
        this.timestamp = Instant.now();
    }
    
    public PSPAuthorizationResponse(boolean success, String pspTransactionId, String status) {
        this.success = success;
        this.pspTransactionId = pspTransactionId;
        this.status = status;
        this.timestamp = Instant.now();
    }
    
    // Static factory methods
    public static PSPAuthorizationResponse success(String pspTransactionId, BigDecimal amount, String currency) {
        PSPAuthorizationResponse response = new PSPAuthorizationResponse(true, pspTransactionId, "AUTHORIZED");
        response.setAuthorizedAmount(amount);
        response.setCurrency(currency);
        return response;
    }
    
    public static PSPAuthorizationResponse declined(String declineCode, String declineMessage) {
        PSPAuthorizationResponse response = new PSPAuthorizationResponse(false, null, "DECLINED");
        response.setDeclineCode(declineCode);
        response.setDeclineMessage(declineMessage);
        return response;
    }
    
    public static PSPAuthorizationResponse error(String errorCode, String errorMessage) {
        PSPAuthorizationResponse response = new PSPAuthorizationResponse(false, null, "ERROR");
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getPspTransactionId() { return pspTransactionId; }
    public void setPspTransactionId(String pspTransactionId) { this.pspTransactionId = pspTransactionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public BigDecimal getAuthorizedAmount() { return authorizedAmount; }
    public void setAuthorizedAmount(BigDecimal authorizedAmount) { this.authorizedAmount = authorizedAmount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getDeclineCode() { return declineCode; }
    public void setDeclineCode(String declineCode) { this.declineCode = declineCode; }
    
    public String getDeclineMessage() { return declineMessage; }
    public void setDeclineMessage(String declineMessage) { this.declineMessage = declineMessage; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
