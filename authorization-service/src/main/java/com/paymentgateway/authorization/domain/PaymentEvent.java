package com.paymentgateway.authorization.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    @Column(name = "event_status", nullable = false, length = 50)
    private String eventStatus;
    
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3)
    private String currency;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "psp_response", columnDefinition = "JSONB")
    private String pspResponse;
    
    @Column(name = "gateway_response", columnDefinition = "JSONB")
    private String gatewayResponse;
    
    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId = UUID.randomUUID();
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    // Constructors
    public PaymentEvent() {}
    
    public PaymentEvent(UUID paymentId, String eventType, String eventStatus) {
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.eventStatus = eventStatus;
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPspResponse() { return pspResponse; }
    public void setPspResponse(String pspResponse) { this.pspResponse = pspResponse; }
    
    public String getGatewayResponse() { return gatewayResponse; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }
    
    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
