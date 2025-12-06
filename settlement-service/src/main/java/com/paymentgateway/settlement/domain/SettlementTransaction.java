package com.paymentgateway.settlement.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_transactions")
public class SettlementTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;
    
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    
    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;
    
    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;
    
    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    
    // Constructors
    public SettlementTransaction() {}
    
    public SettlementTransaction(UUID batchId, UUID paymentId, BigDecimal grossAmount, 
                                BigDecimal feeAmount, BigDecimal netAmount, String currency) {
        this.batchId = batchId;
        this.paymentId = paymentId;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.currency = currency;
    }
    
    // Getters and Setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public UUID getBatchId() {
        return batchId;
    }
    
    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }
    
    public UUID getPaymentId() {
        return paymentId;
    }
    
    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }
    
    public BigDecimal getGrossAmount() {
        return grossAmount;
    }
    
    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }
    
    public BigDecimal getFeeAmount() {
        return feeAmount;
    }
    
    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }
    
    public BigDecimal getNetAmount() {
        return netAmount;
    }
    
    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
