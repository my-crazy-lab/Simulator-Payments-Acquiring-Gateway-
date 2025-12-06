package com.paymentgateway.fraud.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String transactionId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(nullable = false)
    private Double fraudScore;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String triggeredRules;
    
    @Column(nullable = false)
    private String merchantId;
    
    private String ipAddress;
    
    private String deviceFingerprint;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant reviewedAt;
    
    private String reviewedBy;
    
    @Column(columnDefinition = "TEXT")
    private String reviewNotes;
    
    public FraudAlert() {
        this.createdAt = Instant.now();
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public Double getFraudScore() {
        return fraudScore;
    }
    
    public void setFraudScore(Double fraudScore) {
        this.fraudScore = fraudScore;
    }
    
    public FraudStatus getStatus() {
        return status;
    }
    
    public void setStatus(FraudStatus status) {
        this.status = status;
    }
    
    public String getTriggeredRules() {
        return triggeredRules;
    }
    
    public void setTriggeredRules(String triggeredRules) {
        this.triggeredRules = triggeredRules;
    }
    
    public String getMerchantId() {
        return merchantId;
    }
    
    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }
    
    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getReviewedAt() {
        return reviewedAt;
    }
    
    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
    
    public String getReviewedBy() {
        return reviewedBy;
    }
    
    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
    
    public String getReviewNotes() {
        return reviewNotes;
    }
    
    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
