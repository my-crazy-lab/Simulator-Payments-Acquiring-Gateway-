package com.paymentgateway.fraud.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "blacklist", indexes = {
    @Index(name = "idx_blacklist_type_value", columnList = "entryType,value")
})
public class Blacklist {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String entryType;  // IP, CARD_HASH, DEVICE_FINGERPRINT
    
    @Column(nullable = false)
    private String value;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private String createdBy;
    
    public Blacklist() {
        this.createdAt = Instant.now();
    }
    
    public Blacklist(String entryType, String value, String reason) {
        this();
        this.entryType = entryType;
        this.value = value;
        this.reason = reason;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getEntryType() {
        return entryType;
    }
    
    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
