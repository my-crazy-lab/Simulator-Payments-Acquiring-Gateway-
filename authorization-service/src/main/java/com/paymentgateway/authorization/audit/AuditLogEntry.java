package com.paymentgateway.authorization.audit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Builder class for creating detailed audit log entries.
 */
public class AuditLogEntry {
    
    private UUID paymentId;
    private String eventType;
    private String eventStatus;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String pspResponse;
    private String gatewayResponse;
    private Integer processingTimeMs;
    private String errorMessage;
    private String userAgent;
    private String ipAddress;
    private UUID correlationId;
    
    public AuditLogEntry() {}
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public UUID getPaymentId() { return paymentId; }
    public String getEventType() { return eventType; }
    public String getEventStatus() { return eventStatus; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public String getPspResponse() { return pspResponse; }
    public String getGatewayResponse() { return gatewayResponse; }
    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public String getErrorMessage() { return errorMessage; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public UUID getCorrelationId() { return correlationId; }
    
    // Setters
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setDescription(String description) { this.description = description; }
    public void setPspResponse(String pspResponse) { this.pspResponse = pspResponse; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    
    public static class Builder {
        private final AuditLogEntry entry = new AuditLogEntry();
        
        public Builder paymentId(UUID paymentId) {
            entry.paymentId = paymentId;
            return this;
        }
        
        public Builder eventType(String eventType) {
            entry.eventType = eventType;
            return this;
        }
        
        public Builder eventStatus(String eventStatus) {
            entry.eventStatus = eventStatus;
            return this;
        }
        
        public Builder amount(BigDecimal amount) {
            entry.amount = amount;
            return this;
        }
        
        public Builder currency(String currency) {
            entry.currency = currency;
            return this;
        }
        
        public Builder description(String description) {
            entry.description = description;
            return this;
        }
        
        public Builder pspResponse(String pspResponse) {
            entry.pspResponse = pspResponse;
            return this;
        }
        
        public Builder gatewayResponse(String gatewayResponse) {
            entry.gatewayResponse = gatewayResponse;
            return this;
        }
        
        public Builder processingTimeMs(Integer processingTimeMs) {
            entry.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            entry.errorMessage = errorMessage;
            return this;
        }
        
        public Builder userAgent(String userAgent) {
            entry.userAgent = userAgent;
            return this;
        }
        
        public Builder ipAddress(String ipAddress) {
            entry.ipAddress = ipAddress;
            return this;
        }
        
        public Builder correlationId(UUID correlationId) {
            entry.correlationId = correlationId;
            return this;
        }
        
        public AuditLogEntry build() {
            return entry;
        }
    }
}
